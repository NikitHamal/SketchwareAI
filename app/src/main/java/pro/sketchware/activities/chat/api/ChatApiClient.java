package pro.sketchware.activities.chat.api;

import java.util.List;
import java.util.function.Consumer;

import pro.sketchware.activities.chat.models.AIModel;
import pro.sketchware.activities.chat.models.ChatMessage;

public interface ChatApiClient {
    interface MessageListener {
        void onMessageStart();
        void onMessageChunk(String chunk, boolean isComplete);
        void onMessageComplete(String fullMessage, String modelName);
        void onError(String error);
        void onRequestCompleted();
    }

    void sendMessage(
        String message,
        AIModel model,
        List<ChatMessage> history,
        boolean thinkingEnabled,
        boolean webSearchEnabled,
        boolean agentEnabled,
        MessageListener listener
    );

    List<AIModel> getAvailableModels();
    void refreshModels(Consumer<List<AIModel>> callback);
}