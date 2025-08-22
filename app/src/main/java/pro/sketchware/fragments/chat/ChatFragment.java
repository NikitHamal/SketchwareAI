package pro.sketchware.fragments.chat;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import a.a.a.qA;
import pro.sketchware.R;
import pro.sketchware.activities.chat.ChatActivity;
import pro.sketchware.adapters.chat.ConversationAdapter;
import pro.sketchware.databinding.FragmentChatBinding;
import pro.sketchware.models.chat.Conversation;
import pro.sketchware.utility.chat.ConversationManager;

public class ChatFragment extends qA {
    
    private FragmentChatBinding binding;
    private ConversationAdapter conversationAdapter;
    private ConversationManager conversationManager;
    private List<Conversation> conversations = new ArrayList<>();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        conversationManager = ConversationManager.getInstance(requireContext());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentChatBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        setupRecyclerView();
        setupFabButton();
        loadConversations();
    }

    private void setupRecyclerView() {
        conversationAdapter = new ConversationAdapter(conversations, new ConversationAdapter.OnConversationClickListener() {
            @Override
            public void onConversationClick(Conversation conversation) {
                openChatActivity(conversation.getId());
            }

            @Override
            public void onConversationLongClick(Conversation conversation) {
                showConversationOptions(conversation);
            }
        });

        binding.recyclerConversations.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerConversations.setAdapter(conversationAdapter);
    }

    private void setupFabButton() {
        binding.fabNewConversation.setOnClickListener(v -> createNewConversation());
    }

    private void loadConversations() {
        conversations.clear();
        conversations.addAll(conversationManager.getAllConversations());
        updateUI();
    }

    private void updateUI() {
        if (conversations.isEmpty()) {
            binding.layoutEmptyState.setVisibility(View.VISIBLE);
            binding.recyclerConversations.setVisibility(View.GONE);
        } else {
            binding.layoutEmptyState.setVisibility(View.GONE);
            binding.recyclerConversations.setVisibility(View.VISIBLE);
            conversationAdapter.notifyDataSetChanged();
        }
    }

    private void createNewConversation() {
        String conversationId = conversationManager.createNewConversation("New Conversation");
        openChatActivity(conversationId);
    }

    private void openChatActivity(String conversationId) {
        Intent intent = new Intent(requireContext(), ChatActivity.class);
        intent.putExtra(ChatActivity.EXTRA_CONVERSATION_ID, conversationId);
        startActivity(intent);
    }

    private void showConversationOptions(Conversation conversation) {
        // TODO: Implement options dialog (rename, delete, etc.)
    }

    @Override
    public void onResume() {
        super.onResume();
        loadConversations(); // Refresh conversations when returning from ChatActivity
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}