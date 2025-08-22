package pro.sketchware.utility.chat;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import pro.sketchware.models.chat.Conversation;

public class ConversationManager {
    private static final String PREFS_NAME = "chat_conversations";
    private static final String KEY_CONVERSATIONS = "conversations";
    
    private static ConversationManager instance;
    private final SharedPreferences prefs;
    private final Gson gson;
    
    private ConversationManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
    }
    
    public static synchronized ConversationManager getInstance(Context context) {
        if (instance == null) {
            instance = new ConversationManager(context.getApplicationContext());
        }
        return instance;
    }
    
    public List<Conversation> getAllConversations() {
        String json = prefs.getString(KEY_CONVERSATIONS, "[]");
        Type listType = new TypeToken<List<Conversation>>(){}.getType();
        List<Conversation> conversations = gson.fromJson(json, listType);
        
        if (conversations == null) {
            conversations = new ArrayList<>();
        }
        
        // Sort by last message time (newest first)
        Collections.sort(conversations, new Comparator<Conversation>() {
            @Override
            public int compare(Conversation c1, Conversation c2) {
                return Long.compare(c2.getLastMessageAt(), c1.getLastMessageAt());
            }
        });
        
        return conversations;
    }
    
    public String createNewConversation(String title) {
        String id = UUID.randomUUID().toString();
        Conversation conversation = new Conversation(id, title);
        
        List<Conversation> conversations = getAllConversations();
        conversations.add(0, conversation); // Add to beginning
        saveConversations(conversations);
        
        return id;
    }
    
    public Conversation getConversation(String id) {
        List<Conversation> conversations = getAllConversations();
        for (Conversation conversation : conversations) {
            if (conversation.getId().equals(id)) {
                return conversation;
            }
        }
        return null;
    }
    
    public void saveConversation(Conversation conversation) {
        List<Conversation> conversations = getAllConversations();
        
        // Find and replace existing conversation
        boolean found = false;
        for (int i = 0; i < conversations.size(); i++) {
            if (conversations.get(i).getId().equals(conversation.getId())) {
                conversations.set(i, conversation);
                found = true;
                break;
            }
        }
        
        // If not found, add as new
        if (!found) {
            conversations.add(0, conversation);
        }
        
        saveConversations(conversations);
    }
    
    public void deleteConversation(String id) {
        List<Conversation> conversations = getAllConversations();
        conversations.removeIf(c -> c.getId().equals(id));
        saveConversations(conversations);
    }
    
    public void updateConversationTitle(String id, String newTitle) {
        Conversation conversation = getConversation(id);
        if (conversation != null) {
            conversation.setTitle(newTitle);
            saveConversation(conversation);
        }
    }
    
    private void saveConversations(List<Conversation> conversations) {
        String json = gson.toJson(conversations);
        prefs.edit().putString(KEY_CONVERSATIONS, json).apply();
    }
}