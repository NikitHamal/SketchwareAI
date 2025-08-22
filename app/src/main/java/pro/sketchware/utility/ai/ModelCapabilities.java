package pro.sketchware.utility.ai;

public class ModelCapabilities {
    private final boolean supportsThinking;
    private final boolean supportsReasoningEffort;
    private final boolean supportsVision;
    private final boolean supportsAgent;
    private final boolean supportsWebSearch;
    private final boolean supportsFileUpload;
    private final boolean supportsFunctionCalling;
    private final int maxTokens;
    private final int maxOutputTokens;

    public ModelCapabilities(boolean supportsThinking, boolean supportsReasoningEffort, 
                           boolean supportsVision, boolean supportsAgent, boolean supportsWebSearch,
                           boolean supportsFileUpload, boolean supportsFunctionCalling,
                           int maxTokens, int maxOutputTokens) {
        this.supportsThinking = supportsThinking;
        this.supportsReasoningEffort = supportsReasoningEffort;
        this.supportsVision = supportsVision;
        this.supportsAgent = supportsAgent;
        this.supportsWebSearch = supportsWebSearch;
        this.supportsFileUpload = supportsFileUpload;
        this.supportsFunctionCalling = supportsFunctionCalling;
        this.maxTokens = maxTokens;
        this.maxOutputTokens = maxOutputTokens;
    }

    // Getters
    public boolean supportsThinking() { return supportsThinking; }
    public boolean supportsReasoningEffort() { return supportsReasoningEffort; }
    public boolean supportsVision() { return supportsVision; }
    public boolean supportsAgent() { return supportsAgent; }
    public boolean supportsWebSearch() { return supportsWebSearch; }
    public boolean supportsFileUpload() { return supportsFileUpload; }
    public boolean supportsFunctionCalling() { return supportsFunctionCalling; }
    public int getMaxTokens() { return maxTokens; }
    public int getMaxOutputTokens() { return maxOutputTokens; }
}