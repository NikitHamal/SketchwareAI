package pro.sketchware.activities.chat.api;

import android.content.Context;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import pro.sketchware.activities.chat.models.AIModel;
import pro.sketchware.activities.chat.models.ChatMessage;

public class DeepInfraApiClient implements ChatApiClient {
    private static final String TAG = "DeepInfraApiClient";
    
    // API endpoints
    private static final String CHAT_COMPLETIONS_URL = "https://api.deepinfra.com/v1/openai/chat/completions";
    private static final String MODELS_FEATURED_URL = "https://api.deepinfra.com/models/featured";
    
    private final Context context;
    private final OkHttpClient httpClient;
    private final Gson gson;
    private List<AIModel> cachedModels;

    public DeepInfraApiClient(Context context) {
        this.context = context.getApplicationContext();
        this.gson = new Gson();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        
        // Initialize with default models
        initializeDefaultModels();
    }

    private void initializeDefaultModels() {
        cachedModels = new ArrayList<>();
        // Add some popular models as defaults
        cachedModels.add(createModel("deepseek-ai/DeepSeek-V3.1", "DeepSeek V3.1", true, false));
        cachedModels.add(createModel("openai/gpt-oss-120b", "GPT-OSS 120B", true, true));
        cachedModels.add(createModel("openai/gpt-oss-20b", "GPT-OSS 20B", false, true));
        cachedModels.add(createModel("Qwen/Qwen3-235B-A22B-Thinking-2507", "Qwen3 235B Thinking", true, true));
        cachedModels.add(createModel("meta-llama/Llama-3.3-70B-Instruct", "Llama 3.3 70B", false, true));
    }

    private AIModel createModel(String modelId, String displayName, boolean supportsThinking, boolean supportsWebSearch) {
        AIModel model = new AIModel(modelId, displayName);
        model.setSupportsThinking(supportsThinking);
        model.setSupportsWebSearch(supportsWebSearch);
        model.setSupportsAgent(true); // All models support agent
        return model;
    }

    @Override
    public void sendMessage(String message, AIModel model, List<ChatMessage> history, 
                           boolean thinkingEnabled, boolean webSearchEnabled, boolean agentEnabled, 
                           MessageListener listener) {
        
        // Build the request body
        JsonObject requestBody = buildChatRequest(message, model, history, thinkingEnabled, webSearchEnabled, agentEnabled);
        
        RequestBody body = RequestBody.create(
            requestBody.toString(),
            MediaType.parse("application/json")
        );

        Request request = new Request.Builder()
                .url(CHAT_COMPLETIONS_URL)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "text/event-stream")
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android) SketchwareAI/1.0")
                .addHeader("Origin", "https://deepinfra.com")
                .addHeader("Referer", "https://deepinfra.com/")
                .addHeader("X-Deepinfra-Source", "web-embed")
                .build();

        if (listener != null) {
            listener.onMessageStart();
        }

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Request failed", e);
                if (listener != null) {
                    listener.onError("Connection failed: " + e.getMessage());
                    listener.onRequestCompleted();
                }
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    Log.e(TAG, "Request failed with code: " + response.code());
                    if (listener != null) {
                        listener.onError("Request failed: " + response.code());
                        listener.onRequestCompleted();
                    }
                    response.close();
                    return;
                }

                try {
                    processStreamingResponse(response, model != null ? model.getDisplayName() : "SketchAI", listener);
                } catch (Exception e) {
                    Log.e(TAG, "Error processing response", e);
                    if (listener != null) {
                        listener.onError("Error processing response: " + e.getMessage());
                    }
                } finally {
                    response.close();
                    if (listener != null) {
                        listener.onRequestCompleted();
                    }
                }
            }
        });
    }

    private JsonObject buildChatRequest(String message, AIModel model, List<ChatMessage> history,
                                       boolean thinkingEnabled, boolean webSearchEnabled, boolean agentEnabled) {
        JsonObject request = new JsonObject();
        
        // Set model
        String modelId = model != null ? model.getModelId() : "deepseek-ai/DeepSeek-V3.1";
        request.addProperty("model", modelId);
        
        // Build messages array
        JsonArray messages = new JsonArray();
        
        // Add conversation history
        if (history != null) {
            for (ChatMessage msg : history) {
                if (msg.getContent() != null && !msg.getContent().trim().isEmpty()) {
                    JsonObject messageObj = new JsonObject();
                    messageObj.addProperty("role", msg.getSender() == ChatMessage.SENDER_USER ? "user" : "assistant");
                    messageObj.addProperty("content", msg.getContent());
                    messages.add(messageObj);
                }
            }
        }
        
        // Add current user message
        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        userMessage.addProperty("content", message);
        messages.add(userMessage);
        
        request.add("messages", messages);
        
        // Set streaming
        request.addProperty("stream", true);
        
        // Set other parameters
        request.addProperty("temperature", 0.7);
        request.addProperty("max_tokens", 4096);
        
        return request;
    }

    private void processStreamingResponse(Response response, String modelName, MessageListener listener) throws IOException {
        ResponseBody responseBody = response.body();
        if (responseBody == null) return;

        InputStream inputStream = responseBody.byteStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        
        StringBuilder fullMessage = new StringBuilder();
        String line;
        
        try {
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("data: ")) {
                    String data = line.substring(6).trim();
                    
                    if ("[DONE]".equals(data)) {
                        break;
                    }
                    
                    if (!data.isEmpty()) {
                        try {
                            JsonObject jsonData = JsonParser.parseString(data).getAsJsonObject();
                            String content = extractContentFromChunk(jsonData);
                            
                            if (content != null && !content.isEmpty()) {
                                fullMessage.append(content);
                                if (listener != null) {
                                    listener.onMessageChunk(content, false);
                                }
                            }
                        } catch (Exception e) {
                            Log.w(TAG, "Error parsing chunk: " + e.getMessage());
                        }
                    }
                }
            }
        } finally {
            reader.close();
        }
        
        if (listener != null) {
            listener.onMessageComplete(fullMessage.toString(), modelName);
        }
    }

    private String extractContentFromChunk(JsonObject chunk) {
        try {
            if (chunk.has("choices") && chunk.get("choices").isJsonArray()) {
                JsonArray choices = chunk.getAsJsonArray("choices");
                if (choices.size() > 0) {
                    JsonObject choice = choices.get(0).getAsJsonObject();
                    if (choice.has("delta")) {
                        JsonObject delta = choice.getAsJsonObject("delta");
                        if (delta.has("content") && !delta.get("content").isJsonNull()) {
                            return delta.get("content").getAsString();
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error extracting content from chunk", e);
        }
        return null;
    }

    @Override
    public List<AIModel> getAvailableModels() {
        return new ArrayList<>(cachedModels);
    }

    @Override
    public void refreshModels(Consumer<List<AIModel>> callback) {
        Request request = new Request.Builder()
                .url(MODELS_FEATURED_URL)
                .get()
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android) SketchwareAI/1.0")
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Failed to fetch models", e);
                callback.accept(cachedModels); // Return cached models on failure
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String responseBody = response.body().string();
                        Type listType = new TypeToken<List<JsonObject>>(){}.getType();
                        List<JsonObject> modelsJson = gson.fromJson(responseBody, listType);
                        
                        List<AIModel> models = parseModelsFromResponse(modelsJson);
                        cachedModels = models;
                        callback.accept(models);
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing models response", e);
                        callback.accept(cachedModels);
                    }
                } else {
                    callback.accept(cachedModels);
                }
                response.close();
            }
        });
    }

    private List<AIModel> parseModelsFromResponse(List<JsonObject> modelsJson) {
        List<AIModel> models = new ArrayList<>();
        
        for (JsonObject modelJson : modelsJson) {
            try {
                if ("text-generation".equals(modelJson.get("type").getAsString())) {
                    String modelId = modelJson.get("model_name").getAsString();
                    String description = modelJson.has("description") ? 
                        modelJson.get("description").getAsString() : "";
                    
                    AIModel model = new AIModel(modelId, formatDisplayName(modelId), description);
                    
                    // Determine capabilities based on model name/description
                    model.setSupportsThinking(isThinkingModel(modelId, description));
                    model.setSupportsWebSearch(isWebSearchModel(modelId, description));
                    model.setSupportsAgent(true); // All models support agent
                    
                    models.add(model);
                }
            } catch (Exception e) {
                Log.w(TAG, "Error parsing model: " + e.getMessage());
            }
        }
        
        return models;
    }

    private String formatDisplayName(String modelId) {
        // Extract a more readable name from the model ID
        String[] parts = modelId.split("/");
        String name = parts.length > 1 ? parts[1] : modelId;
        
        // Clean up common patterns
        name = name.replace("-Instruct", "").replace("-instruct", "")
                   .replace("Meta-", "").replace("microsoft/", "")
                   .replace("google/", "").replace("deepseek-ai/", "");
        
        return name;
    }

    private boolean isThinkingModel(String modelId, String description) {
        String combined = (modelId + " " + description).toLowerCase();
        return combined.contains("thinking") || combined.contains("reasoning") || 
               combined.contains("cot") || combined.contains("chain-of-thought");
    }

    private boolean isWebSearchModel(String modelId, String description) {
        String combined = (modelId + " " + description).toLowerCase();
        return combined.contains("web") || combined.contains("search") || 
               combined.contains("internet") || combined.contains("browse");
    }
}