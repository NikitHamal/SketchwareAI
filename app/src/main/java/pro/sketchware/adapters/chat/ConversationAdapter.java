package pro.sketchware.adapters.chat;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import pro.sketchware.databinding.ItemConversationBinding;
import pro.sketchware.models.chat.Conversation;

public class ConversationAdapter extends RecyclerView.Adapter<ConversationAdapter.ConversationViewHolder> {
    
    private final List<Conversation> conversations;
    private final OnConversationClickListener listener;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    
    public interface OnConversationClickListener {
        void onConversationClick(Conversation conversation);
        void onConversationLongClick(Conversation conversation);
    }
    
    public ConversationAdapter(List<Conversation> conversations, OnConversationClickListener listener) {
        this.conversations = conversations;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ConversationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemConversationBinding binding = ItemConversationBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ConversationViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ConversationViewHolder holder, int position) {
        holder.bind(conversations.get(position));
    }

    @Override
    public int getItemCount() {
        return conversations.size();
    }

    class ConversationViewHolder extends RecyclerView.ViewHolder {
        private final ItemConversationBinding binding;

        public ConversationViewHolder(ItemConversationBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(Conversation conversation) {
            binding.textConversationTitle.setText(conversation.getTitle());
            binding.textLastMessage.setText(conversation.getLastMessagePreview());
            
            // Format timestamp
            String timeText = getRelativeTimeString(conversation.getLastMessageAt());
            binding.textTimestamp.setText(timeText);
            
            // Set click listeners
            binding.getRoot().setOnClickListener(v -> {
                if (listener != null) {
                    listener.onConversationClick(conversation);
                }
            });
            
            binding.buttonMoreOptions.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onConversationLongClick(conversation);
                }
            });
        }
        
        private String getRelativeTimeString(long timestamp) {
            long now = System.currentTimeMillis();
            long diff = now - timestamp;
            
            if (diff < 60 * 1000) {
                return "Just now";
            } else if (diff < 60 * 60 * 1000) {
                int minutes = (int) (diff / (60 * 1000));
                return minutes + "m ago";
            } else if (diff < 24 * 60 * 60 * 1000) {
                int hours = (int) (diff / (60 * 60 * 1000));
                return hours + "h ago";
            } else {
                return timeFormat.format(new Date(timestamp));
            }
        }
    }
}