package pro.sketchware.models.chat;

public class ChatMessage {
    public static final int SENDER_USER = 0;
    public static final int SENDER_AI = 1;
    
    private String id;
    private String content;
    private int sender;
    private long timestamp;
    private String modelName;
    
    public ChatMessage(String id, String content, int sender) {
        this.id = id;
        this.content = content;
        this.sender = sender;
        this.timestamp = System.currentTimeMillis();
    }
    
    public ChatMessage(String id, String content, int sender, String modelName) {
        this(id, content, sender);
        this.modelName = modelName;
    }

    // Getters
    public String getId() { return id; }
    public String getContent() { return content; }
    public int getSender() { return sender; }
    public long getTimestamp() { return timestamp; }
    public String getModelName() { return modelName; }

    // Setters
    public void setContent(String content) { this.content = content; }
    public void setModelName(String modelName) { this.modelName = modelName; }

    // Utility methods
    public boolean isFromUser() { return sender == SENDER_USER; }
    public boolean isFromAI() { return sender == SENDER_AI; }
}