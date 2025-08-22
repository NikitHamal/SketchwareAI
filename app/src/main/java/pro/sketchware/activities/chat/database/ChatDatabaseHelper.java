package pro.sketchware.activities.chat.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import pro.sketchware.activities.chat.models.ChatConversation;
import pro.sketchware.activities.chat.models.ChatMessage;

public class ChatDatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "sketch_ai_chat.db";
    private static final int DATABASE_VERSION = 1;
    
    // Table names
    private static final String TABLE_CONVERSATIONS = "conversations";
    private static final String TABLE_MESSAGES = "messages";
    
    // Conversations table columns
    private static final String CONV_COL_ID = "id";
    private static final String CONV_COL_TITLE = "title";
    private static final String CONV_COL_LAST_MESSAGE = "last_message";
    private static final String CONV_COL_LAST_MESSAGE_TIME = "last_message_time";
    private static final String CONV_COL_CREATED_TIME = "created_time";
    private static final String CONV_COL_MODEL_NAME = "model_name";
    private static final String CONV_COL_THINKING_ENABLED = "thinking_enabled";
    private static final String CONV_COL_WEB_SEARCH_ENABLED = "web_search_enabled";
    private static final String CONV_COL_AGENT_ENABLED = "agent_enabled";
    
    // Messages table columns
    private static final String MSG_COL_ID = "id";
    private static final String MSG_COL_CONVERSATION_ID = "conversation_id";
    private static final String MSG_COL_SENDER = "sender";
    private static final String MSG_COL_CONTENT = "content";
    private static final String MSG_COL_MODEL_NAME = "model_name";
    private static final String MSG_COL_TIMESTAMP = "timestamp";
    private static final String MSG_COL_IS_THINKING = "is_thinking";
    private static final String MSG_COL_THINKING_CONTENT = "thinking_content";
    
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

    public ChatDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // Create conversations table
        String createConversationsTable = "CREATE TABLE " + TABLE_CONVERSATIONS + " (" +
                CONV_COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                CONV_COL_TITLE + " TEXT, " +
                CONV_COL_LAST_MESSAGE + " TEXT, " +
                CONV_COL_LAST_MESSAGE_TIME + " TEXT, " +
                CONV_COL_CREATED_TIME + " TEXT, " +
                CONV_COL_MODEL_NAME + " TEXT, " +
                CONV_COL_THINKING_ENABLED + " INTEGER DEFAULT 0, " +
                CONV_COL_WEB_SEARCH_ENABLED + " INTEGER DEFAULT 0, " +
                CONV_COL_AGENT_ENABLED + " INTEGER DEFAULT 1" +
                ")";
        
        // Create messages table
        String createMessagesTable = "CREATE TABLE " + TABLE_MESSAGES + " (" +
                MSG_COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                MSG_COL_CONVERSATION_ID + " INTEGER, " +
                MSG_COL_SENDER + " INTEGER, " +
                MSG_COL_CONTENT + " TEXT, " +
                MSG_COL_MODEL_NAME + " TEXT, " +
                MSG_COL_TIMESTAMP + " TEXT, " +
                MSG_COL_IS_THINKING + " INTEGER DEFAULT 0, " +
                MSG_COL_THINKING_CONTENT + " TEXT, " +
                "FOREIGN KEY(" + MSG_COL_CONVERSATION_ID + ") REFERENCES " + TABLE_CONVERSATIONS + "(" + CONV_COL_ID + ")" +
                ")";
        
        db.execSQL(createConversationsTable);
        db.execSQL(createMessagesTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_MESSAGES);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_CONVERSATIONS);
        onCreate(db);
    }

    // Conversation operations
    public long insertConversation(ChatConversation conversation) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        
        values.put(CONV_COL_TITLE, conversation.getTitle());
        values.put(CONV_COL_LAST_MESSAGE, conversation.getLastMessage());
        values.put(CONV_COL_LAST_MESSAGE_TIME, dateFormat.format(conversation.getLastMessageTime()));
        values.put(CONV_COL_CREATED_TIME, dateFormat.format(conversation.getCreatedTime()));
        values.put(CONV_COL_MODEL_NAME, conversation.getModelName());
        values.put(CONV_COL_THINKING_ENABLED, conversation.isThinkingEnabled() ? 1 : 0);
        values.put(CONV_COL_WEB_SEARCH_ENABLED, conversation.isWebSearchEnabled() ? 1 : 0);
        values.put(CONV_COL_AGENT_ENABLED, conversation.isAgentEnabled() ? 1 : 0);
        
        long id = db.insert(TABLE_CONVERSATIONS, null, values);
        conversation.setId(id);
        db.close();
        return id;
    }

    public void updateConversation(ChatConversation conversation) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        
        values.put(CONV_COL_TITLE, conversation.getTitle());
        values.put(CONV_COL_LAST_MESSAGE, conversation.getLastMessage());
        values.put(CONV_COL_LAST_MESSAGE_TIME, dateFormat.format(conversation.getLastMessageTime()));
        values.put(CONV_COL_MODEL_NAME, conversation.getModelName());
        values.put(CONV_COL_THINKING_ENABLED, conversation.isThinkingEnabled() ? 1 : 0);
        values.put(CONV_COL_WEB_SEARCH_ENABLED, conversation.isWebSearchEnabled() ? 1 : 0);
        values.put(CONV_COL_AGENT_ENABLED, conversation.isAgentEnabled() ? 1 : 0);
        
        db.update(TABLE_CONVERSATIONS, values, CONV_COL_ID + " = ?", 
                 new String[]{String.valueOf(conversation.getId())});
        db.close();
    }

    public List<ChatConversation> getAllConversations() {
        List<ChatConversation> conversations = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        
        String query = "SELECT * FROM " + TABLE_CONVERSATIONS + " ORDER BY " + CONV_COL_LAST_MESSAGE_TIME + " DESC";
        Cursor cursor = db.rawQuery(query, null);
        
        if (cursor.moveToFirst()) {
            do {
                ChatConversation conversation = cursorToConversation(cursor);
                conversations.add(conversation);
            } while (cursor.moveToNext());
        }
        
        cursor.close();
        db.close();
        return conversations;
    }

    public ChatConversation getConversation(long id) {
        SQLiteDatabase db = this.getReadableDatabase();
        
        Cursor cursor = db.query(TABLE_CONVERSATIONS, null, CONV_COL_ID + " = ?",
                new String[]{String.valueOf(id)}, null, null, null);
        
        ChatConversation conversation = null;
        if (cursor.moveToFirst()) {
            conversation = cursorToConversation(cursor);
        }
        
        cursor.close();
        db.close();
        return conversation;
    }

    public void deleteConversation(long id) {
        SQLiteDatabase db = this.getWritableDatabase();
        
        // Delete messages first
        db.delete(TABLE_MESSAGES, MSG_COL_CONVERSATION_ID + " = ?", new String[]{String.valueOf(id)});
        
        // Delete conversation
        db.delete(TABLE_CONVERSATIONS, CONV_COL_ID + " = ?", new String[]{String.valueOf(id)});
        
        db.close();
    }

    // Message operations
    public long insertMessage(ChatMessage message) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        
        values.put(MSG_COL_CONVERSATION_ID, message.getConversationId());
        values.put(MSG_COL_SENDER, message.getSender());
        values.put(MSG_COL_CONTENT, message.getContent());
        values.put(MSG_COL_MODEL_NAME, message.getModelName());
        values.put(MSG_COL_TIMESTAMP, dateFormat.format(message.getTimestamp()));
        values.put(MSG_COL_IS_THINKING, message.isThinking() ? 1 : 0);
        values.put(MSG_COL_THINKING_CONTENT, message.getThinkingContent());
        
        long id = db.insert(TABLE_MESSAGES, null, values);
        message.setId(id);
        db.close();
        return id;
    }

    public List<ChatMessage> getMessagesForConversation(long conversationId) {
        List<ChatMessage> messages = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        
        String query = "SELECT * FROM " + TABLE_MESSAGES + 
                      " WHERE " + MSG_COL_CONVERSATION_ID + " = ?" +
                      " ORDER BY " + MSG_COL_TIMESTAMP + " ASC";
        
        Cursor cursor = db.rawQuery(query, new String[]{String.valueOf(conversationId)});
        
        if (cursor.moveToFirst()) {
            do {
                ChatMessage message = cursorToMessage(cursor);
                messages.add(message);
            } while (cursor.moveToNext());
        }
        
        cursor.close();
        db.close();
        return messages;
    }

    private ChatConversation cursorToConversation(Cursor cursor) {
        ChatConversation conversation = new ChatConversation();
        
        conversation.setId(cursor.getLong(cursor.getColumnIndexOrThrow(CONV_COL_ID)));
        conversation.setTitle(cursor.getString(cursor.getColumnIndexOrThrow(CONV_COL_TITLE)));
        conversation.setLastMessage(cursor.getString(cursor.getColumnIndexOrThrow(CONV_COL_LAST_MESSAGE)));
        conversation.setModelName(cursor.getString(cursor.getColumnIndexOrThrow(CONV_COL_MODEL_NAME)));
        conversation.setThinkingEnabled(cursor.getInt(cursor.getColumnIndexOrThrow(CONV_COL_THINKING_ENABLED)) == 1);
        conversation.setWebSearchEnabled(cursor.getInt(cursor.getColumnIndexOrThrow(CONV_COL_WEB_SEARCH_ENABLED)) == 1);
        conversation.setAgentEnabled(cursor.getInt(cursor.getColumnIndexOrThrow(CONV_COL_AGENT_ENABLED)) == 1);
        
        try {
            String lastMessageTimeStr = cursor.getString(cursor.getColumnIndexOrThrow(CONV_COL_LAST_MESSAGE_TIME));
            String createdTimeStr = cursor.getString(cursor.getColumnIndexOrThrow(CONV_COL_CREATED_TIME));
            
            if (lastMessageTimeStr != null) {
                conversation.setLastMessageTime(dateFormat.parse(lastMessageTimeStr));
            }
            if (createdTimeStr != null) {
                conversation.setCreatedTime(dateFormat.parse(createdTimeStr));
            }
        } catch (ParseException e) {
            // Use current time if parsing fails
            conversation.setLastMessageTime(new Date());
            conversation.setCreatedTime(new Date());
        }
        
        return conversation;
    }

    private ChatMessage cursorToMessage(Cursor cursor) {
        ChatMessage message = new ChatMessage();
        
        message.setId(cursor.getLong(cursor.getColumnIndexOrThrow(MSG_COL_ID)));
        message.setConversationId(cursor.getLong(cursor.getColumnIndexOrThrow(MSG_COL_CONVERSATION_ID)));
        message.setSender(cursor.getInt(cursor.getColumnIndexOrThrow(MSG_COL_SENDER)));
        message.setContent(cursor.getString(cursor.getColumnIndexOrThrow(MSG_COL_CONTENT)));
        message.setModelName(cursor.getString(cursor.getColumnIndexOrThrow(MSG_COL_MODEL_NAME)));
        message.setThinking(cursor.getInt(cursor.getColumnIndexOrThrow(MSG_COL_IS_THINKING)) == 1);
        message.setThinkingContent(cursor.getString(cursor.getColumnIndexOrThrow(MSG_COL_THINKING_CONTENT)));
        
        try {
            String timestampStr = cursor.getString(cursor.getColumnIndexOrThrow(MSG_COL_TIMESTAMP));
            if (timestampStr != null) {
                message.setTimestamp(dateFormat.parse(timestampStr));
            }
        } catch (ParseException e) {
            message.setTimestamp(new Date());
        }
        
        return message;
    }
}