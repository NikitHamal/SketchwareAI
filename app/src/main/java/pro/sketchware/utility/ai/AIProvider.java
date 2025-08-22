package pro.sketchware.utility.ai;

public enum AIProvider {
    DEEPINFRA("DeepInfra"),
    OPENAI("OpenAI"),
    ANTHROPIC("Anthropic"),
    GOOGLE("Google"),
    MISTRAL("Mistral"),
    LOCAL("Local");
    
    private final String displayName;
    
    AIProvider(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    @Override
    public String toString() {
        return displayName;
    }
}