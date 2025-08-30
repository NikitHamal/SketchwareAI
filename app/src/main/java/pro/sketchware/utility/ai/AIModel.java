package pro.sketchware.utility.ai;

public class AIModel {
    private final String modelId;
    private final String displayName;
    private final AIProvider provider;
    private final ModelCapabilities capabilities;

    public AIModel(String modelId, String displayName, AIProvider provider, ModelCapabilities capabilities) {
        this.modelId = modelId;
        this.displayName = displayName;
        this.provider = provider;
        this.capabilities = capabilities;
    }

    // Getters
    public String getModelId() { return modelId; }
    public String getDisplayName() { return displayName; }
    public AIProvider getProvider() { return provider; }
    public ModelCapabilities getCapabilities() { return capabilities; }
    
    // Utility methods
    public boolean supportsThinking() { return capabilities.supportsThinking(); }
    public boolean supportsWebSearch() { return capabilities.supportsWebSearch(); }
    public boolean supportsVision() { return capabilities.supportsVision(); }
    public boolean supportsAgent() { return capabilities.supportsAgent(); }
    
    @Override
    public String toString() {
        return displayName;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        AIModel aiModel = (AIModel) obj;
        return modelId.equals(aiModel.modelId);
    }
    
    @Override
    public int hashCode() {
        return modelId.hashCode();
    }
}