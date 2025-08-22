package pro.sketchware.activities.chat.adapters;

import android.content.Context;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

import pro.sketchware.R;
import pro.sketchware.activities.chat.models.ChatMessage;

public class MessagesAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    
    private static final int VIEW_TYPE_USER = 0;
    private static final int VIEW_TYPE_AI = 1;

    private final Context context;
    private final List<ChatMessage> messages;

    public MessagesAdapter(Context context) {
        this.context = context;
        this.messages = new ArrayList<>();
    }

    public void setMessages(List<ChatMessage> messages) {
        this.messages.clear();
        this.messages.addAll(messages);
        notifyDataSetChanged();
    }

    public void addMessage(ChatMessage message) {
        messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }

    public void updateLastMessage(ChatMessage message) {
        if (!messages.isEmpty()) {
            int lastIndex = messages.size() - 1;
            messages.set(lastIndex, message);
            notifyItemChanged(lastIndex);
        }
    }

    @Override
    public int getViewType(int position) {
        ChatMessage message = messages.get(position);
        return message.getSender() == ChatMessage.SENDER_USER ? VIEW_TYPE_USER : VIEW_TYPE_AI;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_USER) {
            View view = LayoutInflater.from(context).inflate(R.layout.item_chat_message_user, parent, false);
            return new UserMessageViewHolder(view);
        } else {
            View view = LayoutInflater.from(context).inflate(R.layout.item_chat_message_ai, parent, false);
            return new AIMessageViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage message = messages.get(position);
        
        if (holder instanceof UserMessageViewHolder) {
            ((UserMessageViewHolder) holder).bind(message);
        } else if (holder instanceof AIMessageViewHolder) {
            ((AIMessageViewHolder) holder).bind(message);
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    // User message view holder
    static class UserMessageViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvMessageContent;
        private final TextView tvMessageTime;

        public UserMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMessageContent = itemView.findViewById(R.id.tv_message_content);
            tvMessageTime = itemView.findViewById(R.id.tv_message_time);
        }

        public void bind(ChatMessage message) {
            tvMessageContent.setText(message.getContent());
            
            // Format time
            String timeText = DateFormat.format("HH:mm", message.getTimestamp()).toString();
            tvMessageTime.setText(timeText);
        }
    }

    // AI message view holder
    static class AIMessageViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvModelName;
        private final TextView tvMessageTime;
        private final TextView tvMessageContent;
        private final MaterialCardView cardThinking;
        private final TextView tvThinkingContent;

        public AIMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            tvModelName = itemView.findViewById(R.id.tv_model_name);
            tvMessageTime = itemView.findViewById(R.id.tv_message_time);
            tvMessageContent = itemView.findViewById(R.id.tv_message_content);
            cardThinking = itemView.findViewById(R.id.card_thinking);
            tvThinkingContent = itemView.findViewById(R.id.tv_thinking_content);
        }

        public void bind(ChatMessage message) {
            // Set model name
            String modelName = message.getModelName();
            if (modelName == null || modelName.trim().isEmpty()) {
                modelName = "SketchAI";
            }
            tvModelName.setText(modelName);
            
            // Set message content
            tvMessageContent.setText(message.getContent());
            
            // Format time
            String timeText = DateFormat.format("HH:mm", message.getTimestamp()).toString();
            tvMessageTime.setText(timeText);
            
            // Handle thinking content
            if (message.isThinking() && message.getThinkingContent() != null && !message.getThinkingContent().trim().isEmpty()) {
                cardThinking.setVisibility(View.VISIBLE);
                tvThinkingContent.setText(message.getThinkingContent());
            } else {
                cardThinking.setVisibility(View.GONE);
            }
        }
    }
}