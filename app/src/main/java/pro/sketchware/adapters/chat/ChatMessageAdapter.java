package pro.sketchware.adapters.chat;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import pro.sketchware.databinding.ItemMessageAiBinding;
import pro.sketchware.databinding.ItemMessageUserBinding;
import pro.sketchware.models.chat.ChatMessage;

public class ChatMessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    
    private static final int VIEW_TYPE_USER = 0;
    private static final int VIEW_TYPE_AI = 1;
    
    private final List<ChatMessage> messages;
    private final OnMessageActionListener listener;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    
    public interface OnMessageActionListener {
        void onCopyMessage(ChatMessage message);
        void onShareMessage(ChatMessage message);
    }
    
    public ChatMessageAdapter(List<ChatMessage> messages, OnMessageActionListener listener) {
        this.messages = messages;
        this.listener = listener;
    }

    @Override
    public int getItemViewType(int position) {
        return messages.get(position).isFromUser() ? VIEW_TYPE_USER : VIEW_TYPE_AI;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        
        if (viewType == VIEW_TYPE_USER) {
            ItemMessageUserBinding binding = ItemMessageUserBinding.inflate(inflater, parent, false);
            return new UserMessageViewHolder(binding);
        } else {
            ItemMessageAiBinding binding = ItemMessageAiBinding.inflate(inflater, parent, false);
            return new AIMessageViewHolder(binding);
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
    
    class UserMessageViewHolder extends RecyclerView.ViewHolder {
        private final ItemMessageUserBinding binding;

        public UserMessageViewHolder(ItemMessageUserBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(ChatMessage message) {
            binding.textMessageContent.setText(message.getContent());
            binding.textTimestamp.setText(timeFormat.format(new Date(message.getTimestamp())));
        }
    }
    
    class AIMessageViewHolder extends RecyclerView.ViewHolder {
        private final ItemMessageAiBinding binding;

        public AIMessageViewHolder(ItemMessageAiBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(ChatMessage message) {
            binding.textMessageContent.setText(message.getContent());
            binding.textTimestamp.setText(timeFormat.format(new Date(message.getTimestamp())));
            
            // Set model name
            String modelName = message.getModelName();
            if (modelName != null && !modelName.isEmpty()) {
                binding.textModelName.setText(modelName);
            } else {
                binding.textModelName.setText("AI Assistant");
            }
            
            // Set click listeners for action buttons
            binding.buttonCopyMessage.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onCopyMessage(message);
                }
            });
            
            binding.buttonShareMessage.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onShareMessage(message);
                }
            });
        }
    }
}