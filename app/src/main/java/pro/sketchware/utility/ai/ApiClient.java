package pro.sketchware.utility.ai;

import java.util.List;

import pro.sketchware.models.chat.ChatMessage;

public interface ApiClient {
    
    /**
     * Send a message to the AI and get a response
     */
    void sendMessage(String message, AIModel model, List<ChatMessage> history, 
                    boolean thinkingModeEnabled, boolean webSearchEnabled, boolean agentModeEnabled);
    
    /**
     * Fetch available AI models
     */
    List<AIModel> fetchModels();
}