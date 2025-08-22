package pro.sketchware.utility.ai;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

import pro.sketchware.models.chat.ChatMessage;

public class AIManager {
    private static final String PREFS_NAME = "ai_settings";
    private static final String KEY_SELECTED_MODEL = "selected_model";
    private static final String KEY_THINKING_MODE = "thinking_mode";
    private static final String KEY_WEB_SEARCH = "web_search";
    private static final String KEY_AGENT_MODE = "agent_mode";
    
    private static AIManager instance;
    private final Context context;
    private final SharedPreferences prefs;
    private final DeepInfraApiClient apiClient;
    
    private List<AIModel> availableModels;
    private AIModel selectedModel;
    
    private AIManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.apiClient = new DeepInfraApiClient(context, null); // Will be set per request
        loadSettings();
    }
    
    public static synchronized AIManager getInstance(Context context) {
        if (instance == null) {
            instance = new AIManager(context);
        }
        return instance;
    }
    
    /**
     * Load available models from the API
     */
    public void loadModels(ModelLoadCallback callback) {
        new Thread(() -> {
            try {
                List<AIModel> models = apiClient.fetchModels();
                availableModels = models;
                
                // Set default model if none selected
                if (selectedModel == null && !models.isEmpty()) {
                    selectedModel = models.get(0);
                    saveSelectedModel(selectedModel);
                }
                
                if (callback != null) {
                    callback.onModelsLoaded(models);
                }
            } catch (Exception e) {
                if (callback != null) {
                    callback.onError("Failed to load models: " + e.getMessage());
                }
            }
        }).start();
    }
    
    /**
     * Send a message to AI
     */
    public void sendMessage(String message, List<ChatMessage> history, AIActionListener listener) {
        if (selectedModel == null) {
            if (listener != null) {
                listener.onAiError("No model selected");
            }
            return;
        }
        
        boolean thinkingMode = prefs.getBoolean(KEY_THINKING_MODE, true);
        boolean webSearch = prefs.getBoolean(KEY_WEB_SEARCH, false);
        boolean agentMode = prefs.getBoolean(KEY_AGENT_MODE, true);
        
        // Check model capabilities and adjust settings
        ModelCapabilities caps = selectedModel.getCapabilities();
        if (!caps.supportsThinking()) thinkingMode = false;
        if (!caps.supportsWebSearch()) webSearch = false;
        if (!caps.supportsAgent()) agentMode = false;
        
        DeepInfraApiClient client = new DeepInfraApiClient(context, listener);
        client.sendMessage(message, selectedModel, history, thinkingMode, webSearch, agentMode);
    }
    
    /**
     * Get available models
     */
    public List<AIModel> getAvailableModels() {
        return availableModels != null ? availableModels : new ArrayList<>();
    }
    
    /**
     * Get selected model
     */
    public AIModel getSelectedModel() {
        return selectedModel;
    }
    
    /**
     * Set selected model
     */
    public void setSelectedModel(AIModel model) {
        this.selectedModel = model;
        saveSelectedModel(model);
    }
    
    /**
     * Get thinking mode setting
     */
    public boolean isThinkingModeEnabled() {
        return prefs.getBoolean(KEY_THINKING_MODE, true);
    }
    
    /**
     * Set thinking mode setting
     */
    public void setThinkingModeEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_THINKING_MODE, enabled).apply();
    }
    
    /**
     * Get web search setting
     */
    public boolean isWebSearchEnabled() {
        return prefs.getBoolean(KEY_WEB_SEARCH, false);
    }
    
    /**
     * Set web search setting
     */
    public void setWebSearchEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_WEB_SEARCH, enabled).apply();
    }
    
    /**
     * Get agent mode setting
     */
    public boolean isAgentModeEnabled() {
        return prefs.getBoolean(KEY_AGENT_MODE, true);
    }
    
    /**
     * Set agent mode setting
     */
    public void setAgentModeEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_AGENT_MODE, enabled).apply();
    }
    
    /**
     * Check if a feature is supported by the selected model
     */
    public boolean isFeatureSupported(String feature) {
        if (selectedModel == null) return false;
        
        ModelCapabilities caps = selectedModel.getCapabilities();
        switch (feature.toLowerCase()) {
            case "thinking":
                return caps.supportsThinking();
            case "websearch":
                return caps.supportsWebSearch();
            case "agent":
                return caps.supportsAgent();
            case "vision":
                return caps.supportsVision();
            default:
                return false;
        }
    }
    
    private void loadSettings() {
        String modelId = prefs.getString(KEY_SELECTED_MODEL, null);
        if (modelId != null) {
            // Will be properly set when models are loaded
        }
    }
    
    private void saveSelectedModel(AIModel model) {
        if (model != null) {
            prefs.edit().putString(KEY_SELECTED_MODEL, model.getModelId()).apply();
        }
    }
    
    public interface ModelLoadCallback {
        void onModelsLoaded(List<AIModel> models);
        void onError(String error);
    }
}