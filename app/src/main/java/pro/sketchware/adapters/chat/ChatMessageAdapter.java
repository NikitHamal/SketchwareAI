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
    
    private List<ChatMessage> messages;
    private final OnMessageActionListener listener;
    private Markwon markwon;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    
    public interface OnMessageActionListener {
        void onCopyMessage(ChatMessage message);
        void onShareMessage(ChatMessage message);
    }
    
    public ChatMessageAdapter(List<ChatMessage> messages, OnMessageActionListener listener) {
        this.messages = messages;
        this.listener = listener;
    }

    public void setMessages(List<ChatMessage> messages) {
        this.messages = messages;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return messages.get(position).isFromUser() ? VIEW_TYPE_USER : VIEW_TYPE_AI;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (markwon == null) {
            markwon = Markwon.create(parent.getContext());
        }
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
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads);
            return;
        }

        // Handle payload-based updates for streaming
        if (holder instanceof AIMessageViewHolder) {
            ChatMessage message = messages.get(position);
            for (Object payload : payloads) {
                if ("STREAM_UPDATE".equals(payload)) {
                    ((AIMessageViewHolder) holder).updateContent(message.getContent());
                }
            }
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
            markwon.setMarkdown(binding.textMessageContent, message.getContent());
            binding.textTimestamp.setText(timeFormat.format(new Date(message.getTimestamp())));
        }
    }
    
    class AIMessageViewHolder extends RecyclerView.ViewHolder {
        private final ItemMessageAiBinding binding;

        public AIMessageViewHolder(ItemMessageAiBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void updateContent(String content) {
            markwon.setMarkdown(binding.textMessageContent, content);
        }

        public void bind(ChatMessage message) {
            markwon.setMarkdown(binding.textMessageContent, message.getContent());
            binding.textTimestamp.setText(timeFormat.format(new Date(message.getTimestamp())));

            // Format model name to remove provider prefix if present
            String modelName = message.getModelName();
            if (modelName != null && !modelName.isEmpty()) {
                String display = modelName;
                int slash = modelName.lastIndexOf('/');
                if (slash >= 0 && slash < modelName.length() - 1) {
                    display = modelName.substring(slash + 1);
                }
                binding.textModelName.setText(display);
            } else {
                binding.textModelName.setText("AI Assistant");
            }

            // Thinking/streaming state: show thinking row, hide actions while streaming
            boolean isStreaming = message.isStreaming();
            binding.layoutThinking.setVisibility(isStreaming ? android.view.View.VISIBLE : android.view.View.GONE);
            int actionsVisibility = isStreaming ? android.view.View.GONE : android.view.View.VISIBLE;
            binding.buttonCopyMessage.setVisibility(actionsVisibility);
            binding.buttonShareMessage.setVisibility(actionsVisibility);

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