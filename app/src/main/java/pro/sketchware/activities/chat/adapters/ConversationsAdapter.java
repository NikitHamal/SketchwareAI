package pro.sketchware.activities.chat.adapters;

import android.content.Context;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import pro.sketchware.R;
import pro.sketchware.activities.chat.models.ChatConversation;

public class ConversationsAdapter extends RecyclerView.Adapter<ConversationsAdapter.ConversationViewHolder> {
    
    public interface ConversationClickListener {
        void onConversationClick(ChatConversation conversation);
        void onConversationMenuClick(ChatConversation conversation, View view);
    }

    private final Context context;
    private final List<ChatConversation> conversations;
    private ConversationClickListener listener;

    public ConversationsAdapter(Context context) {
        this.context = context;
        this.conversations = new ArrayList<>();
    }

    public void setConversationClickListener(ConversationClickListener listener) {
        this.listener = listener;
    }

    public void setConversations(List<ChatConversation> conversations) {
        this.conversations.clear();
        this.conversations.addAll(conversations);
        notifyDataSetChanged();
    }

    public void addConversation(ChatConversation conversation) {
        conversations.add(0, conversation); // Add to beginning
        notifyItemInserted(0);
    }

    public void updateConversation(ChatConversation conversation) {
        for (int i = 0; i < conversations.size(); i++) {
            if (conversations.get(i).getId() == conversation.getId()) {
                conversations.set(i, conversation);
                notifyItemChanged(i);
                break;
            }
        }
    }

    public void removeConversation(long conversationId) {
        for (int i = 0; i < conversations.size(); i++) {
            if (conversations.get(i).getId() == conversationId) {
                conversations.remove(i);
                notifyItemRemoved(i);
                break;
            }
        }
    }

    @NonNull
    @Override
    public ConversationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_conversation, parent, false);
        return new ConversationViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ConversationViewHolder holder, int position) {
        ChatConversation conversation = conversations.get(position);
        holder.bind(conversation);
    }

    @Override
    public int getItemCount() {
        return conversations.size();
    }

    class ConversationViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvTitle;
        private final TextView tvTime;
        private final TextView tvLastMessage;
        private final TextView tvModelName;
        private final ImageButton btnMenu;

        public ConversationViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_conversation_title);
            tvTime = itemView.findViewById(R.id.tv_conversation_time);
            tvLastMessage = itemView.findViewById(R.id.tv_last_message);
            tvModelName = itemView.findViewById(R.id.tv_model_name);
            btnMenu = itemView.findViewById(R.id.btn_conversation_menu);
        }

        public void bind(ChatConversation conversation) {
            // Set title
            String title = conversation.getTitle();
            if (title == null || title.trim().isEmpty()) {
                title = "New Conversation";
            }
            tvTitle.setText(title);

            // Set last message
            String lastMessage = conversation.getLastMessage();
            if (lastMessage == null || lastMessage.trim().isEmpty()) {
                lastMessage = "Start a conversation...";
            }
            tvLastMessage.setText(lastMessage);

            // Set model name
            String modelName = conversation.getModelName();
            if (modelName == null || modelName.trim().isEmpty()) {
                modelName = "SketchAI";
            }
            tvModelName.setText(modelName);

            // Set time
            Date lastMessageTime = conversation.getLastMessageTime();
            if (lastMessageTime != null) {
                CharSequence timeText = DateUtils.getRelativeTimeSpanString(
                    lastMessageTime.getTime(),
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE
                );
                tvTime.setText(timeText);
            } else {
                tvTime.setText("");
            }

            // Set click listeners
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onConversationClick(conversation);
                }
            });

            btnMenu.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onConversationMenuClick(conversation, v);
                }
            });
        }
    }
}