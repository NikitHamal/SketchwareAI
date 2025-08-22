package pro.sketchware.utility.ai;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSource;
import pro.sketchware.models.chat.ChatMessage;

/**
 * DeepInfraApiClient
 * OpenAI-compatible client for DeepInfra web-embed endpoints.
 * - Chat completions: https://api.deepinfra.com/v1/openai/chat/completions
 * - Models (featured): https://api.deepinfra.com/models/featured
 */
public class DeepInfraApiClient implements ApiClient {
    private static final String TAG = "DeepInfraApiClient";
    private static final String DI_CHAT = "https://api.deepinfra.com/v1/openai/chat/completions";
    private static final String DI_MODELS_FEATURED = "https://api.deepinfra.com/models/featured";

    private final Context context;
    private final AIActionListener actionListener;
    private final OkHttpClient httpClient;
    private final Gson gson = new Gson();

    public DeepInfraApiClient(Context context, AIActionListener actionListener) {
        this.context = context.getApplicationContext();
        this.actionListener = actionListener;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public void sendMessage(String message, AIModel model, List<ChatMessage> history, 
                          boolean thinkingModeEnabled, boolean webSearchEnabled, boolean agentModeEnabled) {
        new Thread(() -> {
            Response response = null;
            try {
                if (actionListener != null) actionListener.onAiRequestStarted();

                String modelId = model != null ? model.getModelId() : "deepseek-v3";
                JsonObject body = buildOpenAIStyleBody(modelId, message, history);

                Request request = new Request.Builder()
                        .url(DI_CHAT)
                        .addHeader("user-agent", "Mozilla/5.0 (Linux; Android) SketchwareAI/1.0")
                        .addHeader("accept", "text/event-stream")
                        .post(RequestBody.create(body.toString(), MediaType.parse("application/json")))
                        .build();

                response = httpClient.newCall(request).execute();
                if (response.isSuccessful() && response.body() != null) {
                    String modelDisplay = model != null ? model.getDisplayName() : (modelId != null ? modelId : "DeepInfra");
                    streamOpenAiSse(response, modelDisplay);
                } else {
                    if (actionListener != null) actionListener.onAiError("DeepInfra error: " + (response != null ? response.code() : -1));
                }
            } catch (Exception e) {
                Log.e(TAG, "Error calling DeepInfra", e);
                if (actionListener != null) actionListener.onAiError("Error: " + e.getMessage());
            } finally {
                try { if (response != null) response.close(); } catch (Exception ignore) {}
                if (actionListener != null) actionListener.onAiRequestCompleted();
            }
        }).start();
    }

    private JsonObject buildOpenAIStyleBody(String modelId, String userMessage, List<ChatMessage> history) {
        JsonArray messages = new JsonArray();
        if (history != null) {
            for (ChatMessage m : history) {
                String role = m.getSender() == ChatMessage.SENDER_USER ? "user" : "assistant";
                String content = m.getContent() != null ? m.getContent() : "";
                if (content.isEmpty()) continue;
                JsonObject msg = new JsonObject();
                msg.addProperty("role", role);
                msg.addProperty("content", content);
                messages.add(msg);
            }
        }
        JsonObject user = new JsonObject();
        user.addProperty("role", "user");
        user.addProperty("content", userMessage);
        messages.add(user);

        JsonObject root = new JsonObject();
        root.addProperty("model", modelId);
        root.add("messages", messages);
        root.addProperty("stream", true);
        return root;
    }

    private void streamOpenAiSse(Response response, String modelDisplayName) throws IOException {
        BufferedSource source = response.body().source();
        try { source.timeout().timeout(60, TimeUnit.SECONDS); } catch (Exception ignore) {}
        StringBuilder eventBuf = new StringBuilder();
        StringBuilder finalText = new StringBuilder();
        long[] lastEmitNs = new long[]{0L};
        int[] lastSentLen = new int[]{0};
        while (true) {
            String line;
            try {
                line = source.readUtf8LineStrict();
            } catch (EOFException eof) { break; }
            catch (java.io.InterruptedIOException timeout) { Log.w(TAG, "DeepInfra SSE read timed out"); break; }
            if (line == null) break;
            if (line.isEmpty()) {
                handleOpenAiEvent(eventBuf.toString(), finalText, lastEmitNs, lastSentLen);
                eventBuf.setLength(0);
                continue;
            }
            eventBuf.append(line).append('\n');
        }
        if (eventBuf.length() > 0) {
            handleOpenAiEvent(eventBuf.toString(), finalText, lastEmitNs, lastSentLen);
        }
        if (actionListener != null) {
            if (finalText.length() != lastSentLen[0]) {
                actionListener.onAiStreamUpdate(finalText.toString(), false);
            }
            try {
                actionListener.onAiResponseComplete(finalText.toString(), modelDisplayName);
            } catch (Exception e) {
                Log.w(TAG, "DeepInfra finalize parse dispatch failed: " + e.getMessage());
            }
        }
    }

    private void handleOpenAiEvent(String rawEvent, StringBuilder finalText, long[] lastEmitNs, int[] lastSentLen) {
        String prefix = "data:";
        int idx = rawEvent.indexOf(prefix);
        if (idx < 0) return;
        String jsonPart = rawEvent.substring(idx + prefix.length()).trim();
        if (jsonPart.isEmpty() || jsonPart.equals("[DONE]")) return;
        try {
            JsonElement elem = JsonParser.parseString(jsonPart);
            if (!elem.isJsonObject()) return;
            JsonObject obj = elem.getAsJsonObject();
            if (obj.has("choices") && obj.get("choices").isJsonArray()) {
                JsonArray choices = obj.getAsJsonArray("choices");
                for (int i = 0; i < choices.size(); i++) {
                    JsonObject choice = choices.get(i).getAsJsonObject();
                    if (choice.has("delta") && choice.get("delta").isJsonObject()) {
                        JsonObject delta = choice.getAsJsonObject("delta");
                        if (delta.has("content") && !delta.get("content").isJsonNull()) {
                            finalText.append(delta.get("content").getAsString());
                            maybeEmit(finalText, lastEmitNs, lastSentLen);
                        }
                    } else if (choice.has("message") && choice.get("message").isJsonObject()) {
                        JsonObject msg = choice.getAsJsonObject("message");
                        if (msg.has("content") && !msg.get("content").isJsonNull()) {
                            finalText.append(msg.get("content").getAsString());
                            maybeEmit(finalText, lastEmitNs, lastSentLen);
                        }
                    }
                }
            }
        } catch (Exception ex) {
            Log.w(TAG, "DeepInfra SSE parse error: " + ex.getMessage());
        }
    }

    private void maybeEmit(StringBuilder buf, long[] lastEmitNs, int[] lastSentLen) {
        if (actionListener == null) return;
        int len = buf.length();
        if (len == lastSentLen[0]) return;
        long now = System.nanoTime();
        long last = lastEmitNs[0];
        boolean timeReady = (last == 0L) || (now - last) >= 40_000_000L; // ~40ms
        boolean sizeReady = (len - lastSentLen[0]) >= 24;
        boolean boundaryReady = len > 0 && buf.charAt(len - 1) == '\n';
        if (timeReady || sizeReady || boundaryReady) {
            actionListener.onAiStreamUpdate(buf.toString(), false);
            lastEmitNs[0] = now;
            lastSentLen[0] = len;
        }
    }

    @Override
    public List<AIModel> fetchModels() {
        List<AIModel> out = new ArrayList<>();
        try {
            OkHttpClient client = httpClient.newBuilder().readTimeout(30, TimeUnit.SECONDS).build();
            Request req = new Request.Builder()
                    .url(DI_MODELS_FEATURED)
                    .addHeader("user-agent", "Mozilla/5.0 (Linux; Android) SketchwareAI/1.0")
                    .addHeader("accept", "*/*")
                    .build();
            try (Response r = client.newCall(req).execute()) {
                if (r.isSuccessful() && r.body() != null) {
                    String body = new String(r.body().bytes(), StandardCharsets.UTF_8);
                    try {
                        SharedPreferences sp = context.getSharedPreferences("ai_deepinfra_models", Context.MODE_PRIVATE);
                        sp.edit().putString("di_models_json", body).putLong("di_models_ts", System.currentTimeMillis()).apply();
                    } catch (Exception ignore) {}

                    try {
                        JsonElement root = JsonParser.parseString(body);
                        if (root.isJsonArray()) {
                            JsonArray arr = root.getAsJsonArray();
                            for (JsonElement e : arr) {
                                if (!e.isJsonObject()) continue;
                                JsonObject m = e.getAsJsonObject();
                                String type = m.has("type") ? m.get("type").getAsString() : "";
                                String id = m.has("model_name") ? m.get("model_name").getAsString() : null;
                                if (id == null || id.isEmpty()) continue;
                                boolean chatCapable = "text-generation".equalsIgnoreCase(type);
                                boolean vision = m.has("reported_type") && "text-to-image".equalsIgnoreCase(m.get("reported_type").getAsString());
                                ModelCapabilities caps = new ModelCapabilities(false, false, vision, true, false, false, false, 131072, 8192);
                                if (chatCapable) {
                                    out.add(new AIModel(id, toDisplayName(id), AIProvider.DEEPINFRA, caps));
                                }
                            }
                        }
                    } catch (Exception parseErr) {
                        Log.w(TAG, "DeepInfra models parse failed", parseErr);
                    }
                }
            }
        } catch (Exception ex) {
            Log.w(TAG, "DeepInfra models fetch failed", ex);
        }
        if (out.isEmpty()) {
            out.add(new AIModel("deepseek-v3", "DeepInfra DeepSeek V3", AIProvider.DEEPINFRA,
                    new ModelCapabilities(true, false, false, true, false, false, false, 131072, 8192)));
        }
        return out;
    }

    private String toDisplayName(String id) {
        String s = id.replace('-', ' ').replace('_', ' ');
        if (s.isEmpty()) return "DeepInfra";
        return s.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + s.substring(1);
    }
}