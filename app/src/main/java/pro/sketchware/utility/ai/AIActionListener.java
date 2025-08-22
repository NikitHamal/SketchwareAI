package pro.sketchware.utility.ai;

public interface AIActionListener {
    
    /**
     * Called when AI request starts
     */
    void onAiRequestStarted();
    
    /**
     * Called when AI request completes
     */
    void onAiRequestCompleted();
    
    /**
     * Called when AI streams response chunks
     */
    void onAiStreamUpdate(String partialResponse, boolean isFinal);
    
    /**
     * Called when AI response is complete
     */
    void onAiResponseComplete(String fullResponse, String modelName);
    
    /**
     * Called when AI request encounters an error
     */
    void onAiError(String error);
}