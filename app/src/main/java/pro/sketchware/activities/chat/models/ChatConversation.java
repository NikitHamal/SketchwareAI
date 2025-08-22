package pro.sketchware.activities.chat.models;

import java.util.Date;
import java.util.List;

public class ChatConversation {
    private long id;
    private String title;
    private String lastMessage;
    private Date lastMessageTime;
    private Date createdTime;
    private String modelName;
    private boolean isThinkingEnabled;
    private boolean isWebSearchEnabled;
    private boolean isAgentEnabled;
    private List<ChatMessage> messages;

    public ChatConversation() {
        this.createdTime = new Date();
        this.lastMessageTime = new Date();
    }

    public ChatConversation(String title) {
        this();
        this.title = title;
    }

    // Getters and setters
    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getLastMessage() {
        return lastMessage;
    }

    public void setLastMessage(String lastMessage) {
        this.lastMessage = lastMessage;
    }

    public Date getLastMessageTime() {
        return lastMessageTime;
    }

    public void setLastMessageTime(Date lastMessageTime) {
        this.lastMessageTime = lastMessageTime;
    }

    public Date getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(Date createdTime) {
        this.createdTime = createdTime;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public boolean isThinkingEnabled() {
        return isThinkingEnabled;
    }

    public void setThinkingEnabled(boolean thinkingEnabled) {
        isThinkingEnabled = thinkingEnabled;
    }

    public boolean isWebSearchEnabled() {
        return isWebSearchEnabled;
    }

    public void setWebSearchEnabled(boolean webSearchEnabled) {
        isWebSearchEnabled = webSearchEnabled;
    }

    public boolean isAgentEnabled() {
        return isAgentEnabled;
    }

    public void setAgentEnabled(boolean agentEnabled) {
        isAgentEnabled = agentEnabled;
    }

    public List<ChatMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<ChatMessage> messages) {
        this.messages = messages;
    }
}