package pro.sketchware.activities.chat.models;

public class AIModel {
    private String modelId;
    private String displayName;
    private String description;
    private boolean supportsThinking;
    private boolean supportsWebSearch;
    private boolean supportsAgent;
    private String type;
    private double inputTokenCost;
    private double outputTokenCost;

    public AIModel() {}

    public AIModel(String modelId, String displayName) {
        this.modelId = modelId;
        this.displayName = displayName;
        this.supportsAgent = true; // All models support agent by default
    }

    public AIModel(String modelId, String displayName, String description) {
        this(modelId, displayName);
        this.description = description;
    }

    // Getters and setters
    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean supportsThinking() {
        return supportsThinking;
    }

    public void setSupportsThinking(boolean supportsThinking) {
        this.supportsThinking = supportsThinking;
    }

    public boolean supportsWebSearch() {
        return supportsWebSearch;
    }

    public void setSupportsWebSearch(boolean supportsWebSearch) {
        this.supportsWebSearch = supportsWebSearch;
    }

    public boolean supportsAgent() {
        return supportsAgent;
    }

    public void setSupportsAgent(boolean supportsAgent) {
        this.supportsAgent = supportsAgent;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public double getInputTokenCost() {
        return inputTokenCost;
    }

    public void setInputTokenCost(double inputTokenCost) {
        this.inputTokenCost = inputTokenCost;
    }

    public double getOutputTokenCost() {
        return outputTokenCost;
    }

    public void setOutputTokenCost(double outputTokenCost) {
        this.outputTokenCost = outputTokenCost;
    }

    @Override
    public String toString() {
        return displayName != null ? displayName : modelId;
    }
}