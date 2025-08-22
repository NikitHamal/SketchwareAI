package pro.sketchware.models.chat;

import java.util.ArrayList;
import java.util.List;

public class Conversation {
    private String id;
    private String title;
    private long createdAt;
    private long lastMessageAt;
    private List<ChatMessage> messages;

    public Conversation(String id, String title) {
        this.id = id;
        this.title = title;
        this.createdAt = System.currentTimeMillis();
        this.lastMessageAt = createdAt;
        this.messages = new ArrayList<>();
    }

    // Getters
    public String getId() { return id; }
    public String getTitle() { return title; }
    public long getCreatedAt() { return createdAt; }
    public long getLastMessageAt() { return lastMessageAt; }
    public List<ChatMessage> getMessages() { return messages; }

    // Setters
    public void setTitle(String title) { this.title = title; }
    public void setLastMessageAt(long lastMessageAt) { this.lastMessageAt = lastMessageAt; }

    // Utility methods
    public void addMessage(ChatMessage message) {
        messages.add(message);
        this.lastMessageAt = message.getTimestamp();
    }

    public ChatMessage getLastMessage() {
        return messages.isEmpty() ? null : messages.get(messages.size() - 1);
    }

    public String getLastMessagePreview() {
        ChatMessage lastMessage = getLastMessage();
        if (lastMessage == null) return "No messages yet";
        
        String content = lastMessage.getContent();
        if (content.length() > 100) {
            return content.substring(0, 100) + "...";
        }
        return content;
    }
}