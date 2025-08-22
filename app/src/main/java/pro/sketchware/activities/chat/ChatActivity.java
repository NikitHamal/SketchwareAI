package pro.sketchware.activities.chat;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import pro.sketchware.R;
import pro.sketchware.adapters.chat.ChatMessageAdapter;
import pro.sketchware.databinding.ActivityChatBinding;
import pro.sketchware.databinding.BottomSheetChatSettingsBinding;
import pro.sketchware.databinding.DialogModelSelectionBinding;
import pro.sketchware.models.chat.ChatMessage;
import pro.sketchware.models.chat.Conversation;
import pro.sketchware.utility.chat.ConversationManager;
import pro.sketchware.utility.ai.AIManager;
import pro.sketchware.utility.ai.AIActionListener;
import pro.sketchware.utility.ai.AIModel;

public class ChatActivity extends BaseAppCompatActivity {
    public static final String EXTRA_CONVERSATION_ID = "conversation_id";
    
    private ActivityChatBinding binding;
    private ChatMessageAdapter messageAdapter;
    private ConversationManager conversationManager;
    private AIManager aiManager;
    private Conversation currentConversation;
    private String conversationId;
    
    // Chat settings
    private boolean thinkingModeEnabled = true;
    private boolean webSearchEnabled = false;
    private boolean agentModeEnabled = true;
    private String selectedModel = "Gemini 2.5 Flash";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        // Get conversation ID from intent
        conversationId = getIntent().getStringExtra(EXTRA_CONVERSATION_ID);
        if (conversationId == null) {
            finish();
            return;
        }
        
        conversationManager = ConversationManager.getInstance(this);
        aiManager = AIManager.getInstance(this);
        currentConversation = conversationManager.getConversation(conversationId);
        
        if (currentConversation == null) {
            finish();
            return;
        }
        
        setupUI();
        setupToolbar();
        setupMessagesSection();
        setupInputSection();
        loadMessages();
        loadAISettings();
    }
    
    private void setupUI() {
        // Configure edge-to-edge if available
        try {
            enableEdgeToEdgeNoContrast();
            
            // Handle window insets for edge-to-edge design
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
                androidx.core.graphics.Insets systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
                androidx.core.graphics.Insets ime = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime());
                
                // Apply top inset to AppBarLayout
                binding.appBarLayout.setPadding(
                    binding.appBarLayout.getPaddingLeft(),
                    systemBars.top,
                    binding.appBarLayout.getPaddingRight(),
                    binding.appBarLayout.getPaddingBottom()
                );
                
                // Apply bottom inset to input section
                int bottomInset = Math.max(systemBars.bottom, ime.bottom);
                binding.layoutInputSection.setPadding(
                    binding.layoutInputSection.getPaddingLeft(),
                    binding.layoutInputSection.getPaddingTop(),
                    binding.layoutInputSection.getPaddingRight(),
                    12 + bottomInset // 12dp base padding + system inset
                );
                
                return insets;
            });
        } catch (Exception e) {
            // Fallback for older versions
        }
    }
    
    private void setupToolbar() {
        binding.textConversationTitle.setText(currentConversation.getTitle());
        
        binding.buttonBack.setOnClickListener(v -> finish());
        
        binding.buttonMoreOptions.setOnClickListener(v -> showConversationOptions());
    }
    
    private void setupMessagesSection() {
        messageAdapter = new ChatMessageAdapter(currentConversation.getMessages(), new ChatMessageAdapter.OnMessageActionListener() {
            @Override
            public void onCopyMessage(ChatMessage message) {
                copyMessageToClipboard(message);
            }
            
            @Override
            public void onShareMessage(ChatMessage message) {
                shareMessage(message);
            }
        });
        
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true); // Start from bottom
        binding.recyclerChatMessages.setLayoutManager(layoutManager);
        binding.recyclerChatMessages.setAdapter(messageAdapter);
        
        // Auto-scroll to bottom when new messages are added
        messageAdapter.registerAdapterDataObserver(new androidx.recyclerview.widget.RecyclerView.AdapterDataObserver() {
            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                binding.recyclerChatMessages.scrollToPosition(messageAdapter.getItemCount() - 1);
            }
        });
    }
    
    private void setupInputSection() {
        // Send button
        binding.buttonSend.setOnClickListener(v -> sendMessage());
        
        // Settings button
        binding.buttonSettings.setOnClickListener(v -> showChatSettings());
        
        // Model selector
        binding.layoutModelSelector.setOnClickListener(v -> showModelSelection());
        
        // Text change listener for send button state
        binding.editTextMessage.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                binding.buttonSend.setEnabled(s.toString().trim().length() > 0);
            }
            
            @Override
            public void afterTextChanged(Editable s) {}
        });
        
        // Update model selector text
        binding.textSelectedModel.setText(selectedModel);
        
        // Initially disable send button
        binding.buttonSend.setEnabled(false);
    }
    
    private void loadMessages() {
        if (currentConversation.getMessages().isEmpty()) {
            binding.layoutEmptyMessages.setVisibility(View.VISIBLE);
            binding.recyclerChatMessages.setVisibility(View.GONE);
        } else {
            binding.layoutEmptyMessages.setVisibility(View.GONE);
            binding.recyclerChatMessages.setVisibility(View.VISIBLE);
            messageAdapter.notifyDataSetChanged();
        }
    }
    
    private void loadAISettings() {
        // Load current AI settings
        thinkingModeEnabled = aiManager.isThinkingModeEnabled();
        webSearchEnabled = aiManager.isWebSearchEnabled();
        agentModeEnabled = aiManager.isAgentModeEnabled();
        
        // Load selected model
        AIModel model = aiManager.getSelectedModel();
        if (model != null) {
            selectedModel = model.getDisplayName();
            binding.textSelectedModel.setText(selectedModel);
        }
        
        // Load available models if not loaded
        aiManager.loadModels(new AIManager.ModelLoadCallback() {
            @Override
            public void onModelsLoaded(List<AIModel> models) {
                runOnUiThread(() -> {
                    // Models loaded successfully
                    AIModel currentModel = aiManager.getSelectedModel();
                    if (currentModel != null) {
                        selectedModel = currentModel.getDisplayName();
                        binding.textSelectedModel.setText(selectedModel);
                    }
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(ChatActivity.this, "Failed to load AI models: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    
    private void sendMessage() {
        String messageText = binding.editTextMessage.getText().toString().trim();
        if (messageText.isEmpty()) return;
        
        // Create user message
        ChatMessage userMessage = new ChatMessage(
            UUID.randomUUID().toString(),
            messageText,
            ChatMessage.SENDER_USER
        );
        
        // Add to conversation
        currentConversation.addMessage(userMessage);
        
        // Update UI
        binding.editTextMessage.setText("");
        updateMessagesUI();
        
        // Save conversation
        conversationManager.saveConversation(currentConversation);
        
        // TODO: Send to AI and handle response
        sendToAI(messageText);
    }
    
    private void sendToAI(String userMessage) {
        aiManager.sendMessage(userMessage, currentConversation.getMessages(), new AIActionListener() {
            @Override
            public void onAiRequestStarted() {
                runOnUiThread(() -> {
                    binding.buttonSend.setEnabled(false);
                    // TODO: Show loading indicator
                });
            }
            
            @Override
            public void onAiRequestCompleted() {
                runOnUiThread(() -> {
                    binding.buttonSend.setEnabled(true);
                    // TODO: Hide loading indicator
                });
            }
            
            @Override
            public void onAiStreamUpdate(String partialResponse, boolean isFinal) {
                runOnUiThread(() -> {
                    // TODO: Update AI message with streaming content
                });
            }
            
            @Override
            public void onAiResponseComplete(String fullResponse, String modelName) {
                runOnUiThread(() -> {
                    ChatMessage aiMessage = new ChatMessage(
                        UUID.randomUUID().toString(),
                        fullResponse,
                        ChatMessage.SENDER_AI,
                        modelName
                    );
                    
                    currentConversation.addMessage(aiMessage);
                    updateMessagesUI();
                    conversationManager.saveConversation(currentConversation);
                    
                    // Update conversation title if it's the first exchange
                    if (currentConversation.getMessages().size() == 2) {
                        updateConversationTitle(currentConversation.getMessages().get(0).getContent());
                    }
                });
            }
            
            @Override
            public void onAiError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(ChatActivity.this, "AI Error: " + error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }
    
    private void updateMessagesUI() {
        if (currentConversation.getMessages().isEmpty()) {
            binding.layoutEmptyMessages.setVisibility(View.VISIBLE);
            binding.recyclerChatMessages.setVisibility(View.GONE);
        } else {
            binding.layoutEmptyMessages.setVisibility(View.GONE);
            binding.recyclerChatMessages.setVisibility(View.VISIBLE);
            messageAdapter.notifyDataSetChanged();
        }
    }
    
    private void updateConversationTitle(String firstMessage) {
        String title = firstMessage.length() > 30 ? 
            firstMessage.substring(0, 30) + "..." : firstMessage;
        
        currentConversation.setTitle(title);
        binding.textConversationTitle.setText(title);
        conversationManager.saveConversation(currentConversation);
    }
    
    private void showChatSettings() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        BottomSheetChatSettingsBinding settingsBinding = BottomSheetChatSettingsBinding.inflate(getLayoutInflater());
        
        // Set current values
        settingsBinding.switchThinkingMode.setChecked(aiManager.isThinkingModeEnabled());
        settingsBinding.switchWebSearch.setChecked(aiManager.isWebSearchEnabled());
        settingsBinding.switchAgentMode.setChecked(aiManager.isAgentModeEnabled());
        
        // Update UI based on model capabilities
        AIModel currentModel = aiManager.getSelectedModel();
        if (currentModel != null) {
            settingsBinding.sectionThinkingMode.setVisibility(
                currentModel.supportsThinking() ? View.VISIBLE : View.GONE);
            settingsBinding.sectionWebSearch.setVisibility(
                currentModel.supportsWebSearch() ? View.VISIBLE : View.GONE);
        }
        
        // Set listeners
        settingsBinding.switchThinkingMode.setOnCheckedChangeListener((v, isChecked) -> {
            aiManager.setThinkingModeEnabled(isChecked);
        });
        
        settingsBinding.switchWebSearch.setOnCheckedChangeListener((v, isChecked) -> {
            aiManager.setWebSearchEnabled(isChecked);
        });
        
        settingsBinding.switchAgentMode.setOnCheckedChangeListener((v, isChecked) -> {
            aiManager.setAgentModeEnabled(isChecked);
        });
        
        dialog.setContentView(settingsBinding.getRoot());
        dialog.show();
    }
    
    private void showModelSelection() {
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this);
        DialogModelSelectionBinding modelBinding = DialogModelSelectionBinding.inflate(getLayoutInflater());
        
        // Load models from AI manager
        List<AIModel> models = aiManager.getAvailableModels();
        
        if (models.isEmpty()) {
            // Show loading or error state
            Toast.makeText(this, "No models available. Please check your connection.", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Set current selection
        AIModel currentModel = aiManager.getSelectedModel();
        for (int i = 0; i < models.size(); i++) {
            if (models.get(i).equals(currentModel)) {
                // TODO: Set radio button selection
                break;
            }
        }
        
        dialog.setView(modelBinding.getRoot());
        
        modelBinding.buttonCancel.setOnClickListener(v -> dialog.create().dismiss());
        modelBinding.buttonSelect.setOnClickListener(v -> {
            // TODO: Get selected model from radio group
            AIModel newModel = models.get(0); // Placeholder - should get from radio selection
            aiManager.setSelectedModel(newModel);
            selectedModel = newModel.getDisplayName();
            binding.textSelectedModel.setText(selectedModel);
            dialog.create().dismiss();
        });
        
        modelBinding.buttonCloseDialog.setOnClickListener(v -> dialog.create().dismiss());
        
        dialog.show();
    }
    
    private void showConversationOptions() {
        new MaterialAlertDialogBuilder(this)
            .setTitle("Conversation Options")
            .setItems(new String[]{"Rename", "Clear Messages", "Delete Conversation"}, (dialog, which) -> {
                switch (which) {
                    case 0:
                        showRenameDialog();
                        break;
                    case 1:
                        clearMessages();
                        break;
                    case 2:
                        deleteConversation();
                        break;
                }
            })
            .show();
    }
    
    private void showRenameDialog() {
        // TODO: Implement rename dialog
        Toast.makeText(this, "Rename functionality will be implemented", Toast.LENGTH_SHORT).show();
    }
    
    private void clearMessages() {
        new MaterialAlertDialogBuilder(this)
            .setTitle("Clear Messages")
            .setMessage("Are you sure you want to clear all messages in this conversation?")
            .setPositiveButton("Clear", (dialog, which) -> {
                currentConversation.getMessages().clear();
                updateMessagesUI();
                conversationManager.saveConversation(currentConversation);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    private void deleteConversation() {
        new MaterialAlertDialogBuilder(this)
            .setTitle("Delete Conversation")
            .setMessage("Are you sure you want to delete this conversation? This action cannot be undone.")
            .setPositiveButton("Delete", (dialog, which) -> {
                conversationManager.deleteConversation(conversationId);
                finish();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    private void copyMessageToClipboard(ChatMessage message) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Chat Message", message.getContent());
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, "Message copied to clipboard", Toast.LENGTH_SHORT).show();
    }
    
    private void shareMessage(ChatMessage message) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, message.getContent());
        startActivity(Intent.createChooser(shareIntent, "Share message"));
    }
}