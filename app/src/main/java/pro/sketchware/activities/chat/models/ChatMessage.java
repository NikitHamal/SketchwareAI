package pro.sketchware.activities.chat.models;

import java.util.Date;

public class ChatMessage {
    public static final int SENDER_USER = 0;
    public static final int SENDER_AI = 1;

    private long id;
    private long conversationId;
    private int sender; // SENDER_USER or SENDER_AI
    private String content;
    private String modelName;
    private Date timestamp;
    private boolean isThinking;
    private String thinkingContent;

    public ChatMessage() {
        this.timestamp = new Date();
    }

    public ChatMessage(long conversationId, int sender, String content) {
        this();
        this.conversationId = conversationId;
        this.sender = sender;
        this.content = content;
    }

    public ChatMessage(long conversationId, int sender, String content, String modelName) {
        this(conversationId, sender, content);
        this.modelName = modelName;
    }

    // Getters and setters
    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getConversationId() {
        return conversationId;
    }

    public void setConversationId(long conversationId) {
        this.conversationId = conversationId;
    }

    public int getSender() {
        return sender;
    }

    public void setSender(int sender) {
        this.sender = sender;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isThinking() {
        return isThinking;
    }

    public void setThinking(boolean thinking) {
        isThinking = thinking;
    }

    public String getThinkingContent() {
        return thinkingContent;
    }

    public void setThinkingContent(String thinkingContent) {
        this.thinkingContent = thinkingContent;
    }
}