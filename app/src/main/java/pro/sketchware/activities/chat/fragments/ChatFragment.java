package pro.sketchware.activities.chat.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.transition.MaterialFadeThrough;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import pro.sketchware.R;
import pro.sketchware.activities.chat.activities.ChatActivity;
import pro.sketchware.activities.chat.adapters.ConversationsAdapter;
import pro.sketchware.activities.chat.database.ChatDatabaseHelper;
import pro.sketchware.activities.chat.models.ChatConversation;

public class ChatFragment extends Fragment implements ConversationsAdapter.ConversationClickListener {
    
    private RecyclerView recyclerConversations;
    private View layoutEmptyState;
    private MaterialButton btnNewConversation;
    private MaterialButton btnStartChat;
    
    private ConversationsAdapter conversationsAdapter;
    private ChatDatabaseHelper databaseHelper;
    private ExecutorService executorService;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setEnterTransition(new MaterialFadeThrough());
        setReturnTransition(new MaterialFadeThrough());
        setExitTransition(new MaterialFadeThrough());
        setReenterTransition(new MaterialFadeThrough());
        
        databaseHelper = new ChatDatabaseHelper(requireContext());
        executorService = Executors.newSingleThreadExecutor();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_chat, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        initializeViews(view);
        setupRecyclerView();
        setupClickListeners();
        loadConversations();
    }

    private void initializeViews(View view) {
        recyclerConversations = view.findViewById(R.id.recycler_conversations);
        layoutEmptyState = view.findViewById(R.id.layout_empty_state);
        btnNewConversation = view.findViewById(R.id.btn_new_conversation);
        btnStartChat = view.findViewById(R.id.btn_start_chat);
    }

    private void setupRecyclerView() {
        conversationsAdapter = new ConversationsAdapter(requireContext());
        conversationsAdapter.setConversationClickListener(this);
        
        recyclerConversations.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerConversations.setAdapter(conversationsAdapter);
        recyclerConversations.setHasFixedSize(true);
    }

    private void setupClickListeners() {
        btnNewConversation.setOnClickListener(v -> createNewConversation());
        btnStartChat.setOnClickListener(v -> createNewConversation());
    }

    private void loadConversations() {
        executorService.execute(() -> {
            List<ChatConversation> conversations = databaseHelper.getAllConversations();
            
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    conversationsAdapter.setConversations(conversations);
                    updateEmptyState(conversations.isEmpty());
                });
            }
        });
    }

    private void updateEmptyState(boolean isEmpty) {
        if (isEmpty) {
            recyclerConversations.setVisibility(View.GONE);
            layoutEmptyState.setVisibility(View.VISIBLE);
        } else {
            recyclerConversations.setVisibility(View.VISIBLE);
            layoutEmptyState.setVisibility(View.GONE);
        }
    }

    private void createNewConversation() {
        // Create a new conversation and open the chat activity
        ChatConversation newConversation = new ChatConversation("New Conversation");
        newConversation.setModelName("DeepSeek V3.1");
        newConversation.setAgentEnabled(true);
        
        executorService.execute(() -> {
            long conversationId = databaseHelper.insertConversation(newConversation);
            newConversation.setId(conversationId);
            
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    conversationsAdapter.addConversation(newConversation);
                    updateEmptyState(false);
                    openChatActivity(newConversation);
                });
            }
        });
    }

    private void openChatActivity(ChatConversation conversation) {
        Intent intent = new Intent(requireContext(), ChatActivity.class);
        intent.putExtra("conversation_id", conversation.getId());
        startActivity(intent);
    }

    @Override
    public void onConversationClick(ChatConversation conversation) {
        openChatActivity(conversation);
    }

    @Override
    public void onConversationMenuClick(ChatConversation conversation, View view) {
        PopupMenu popupMenu = new PopupMenu(requireContext(), view);
        popupMenu.getMenuInflater().inflate(R.menu.conversation_menu, popupMenu.getMenu());
        
        popupMenu.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.action_rename) {
                showRenameDialog(conversation);
                return true;
            } else if (itemId == R.id.action_delete) {
                showDeleteDialog(conversation);
                return true;
            }
            return false;
        });
        
        popupMenu.show();
    }

    private void showRenameDialog(ChatConversation conversation) {
        // TODO: Implement rename dialog
        // For now, just show a simple alert
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Rename Conversation")
                .setMessage("This feature will be implemented soon.")
                .setPositiveButton("OK", null)
                .show();
    }

    private void showDeleteDialog(ChatConversation conversation) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete Conversation")
                .setMessage("Are you sure you want to delete this conversation? This action cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> deleteConversation(conversation))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteConversation(ChatConversation conversation) {
        executorService.execute(() -> {
            databaseHelper.deleteConversation(conversation.getId());
            
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    conversationsAdapter.removeConversation(conversation.getId());
                    updateEmptyState(conversationsAdapter.getItemCount() == 0);
                });
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        // Refresh conversations list when returning to this fragment
        loadConversations();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
        if (databaseHelper != null) {
            databaseHelper.close();
        }
    }
}