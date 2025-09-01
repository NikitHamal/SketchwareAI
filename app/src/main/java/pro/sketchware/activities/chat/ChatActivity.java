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
import android.widget.RadioGroup;

import androidx.recyclerview.widget.LinearLayoutManager;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.radiobutton.MaterialRadioButton;

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
    // Streaming state
    private ChatMessage streamingMessage = null;
    private int streamingMessagePosition = -1;
    
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
        
        conversationManager = ConversationManager.getInstance(this);
        aiManager = AIManager.getInstance(this);
        
        // Get conversation ID from intent. If it's null, it's a new conversation.
        conversationId = getIntent().getStringExtra(EXTRA_CONVERSATION_ID);
        if (conversationId != null) {
            currentConversation = conversationManager.getConversation(conversationId);
            if (currentConversation == null) {
                // Invalid ID, treat as new conversation or finish?
                // For now, let's finish to avoid issues. A toast could be helpful.
                Toast.makeText(this, "Conversation not found.", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
        } else {
            // It's a new conversation, currentConversation remains null until first message.
            currentConversation = null;
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
        } catch (Exception e) {
            // Fallback for older versions
        }

        // Handle keyboard insets to prevent overlap
        ViewCompat.setOnApplyWindowInsetsListener(binding.mainContentContainer, (v, insets) -> {
            int imeHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            int systemBarsHeight = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;

            // Apply padding to the container to push content up
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), Math.max(imeHeight, systemBarsHeight));

            return insets;
        });
    }
    
    private void setupToolbar() {
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_more_options) {
                if (currentConversation != null) {
                    showConversationOptions();
                }
                return true;
            }
            return false;
        });

        if (currentConversation != null) {
            binding.toolbar.setTitle(currentConversation.getTitle());
            binding.toolbar.getMenu().findItem(R.id.action_more_options).setVisible(true);
        } else {
            binding.toolbar.setTitle("New Conversation");
            binding.toolbar.getMenu().findItem(R.id.action_more_options).setVisible(false);
        }
    }
    
    private void setupMessagesSection() {
        List<ChatMessage> messages = (currentConversation != null) ? currentConversation.getMessages() : new ArrayList<>();
        messageAdapter = new ChatMessageAdapter(messages, new ChatMessageAdapter.OnMessageActionListener() {
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
        
        // Send via keyboard IME action
        binding.editTextMessage.setOnEditorActionListener((v, actionId, event) -> {
            // Trigger send for actionId == EditorInfo.IME_ACTION_SEND or when newline with empty modifiers
            try {
                int sendId = android.view.inputmethod.EditorInfo.IME_ACTION_SEND;
                if (actionId == sendId) {
                    sendMessage();
                    return true;
                }
            } catch (Exception ignored) {}
            return false;
        });
        
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
        binding.textSelectedModel.setText(cleanModelName(selectedModel));
        
        // Initially disable send button
        binding.buttonSend.setEnabled(false);
    }
    
    private void loadMessages() {
        if (currentConversation == null || currentConversation.getMessages().isEmpty()) {
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
            binding.textSelectedModel.setText(cleanModelName(selectedModel));
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
                        binding.textSelectedModel.setText(cleanModelName(selectedModel));
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

        boolean isNewConversation = (currentConversation == null);
        if (isNewConversation) {
            // This is the first message. Create the conversation.
            conversationId = UUID.randomUUID().toString();
            currentConversation = new Conversation(conversationId, "New Conversation"); // Temp title
            messageAdapter.setMessages(currentConversation.getMessages());
            binding.toolbar.getMenu().findItem(R.id.action_more_options).setVisible(true);
        }
        
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
        
        // Send to AI
        sendToAI(messageText);
    }
    
    private void sendToAI(String userMessage) {
        aiManager.sendMessage(userMessage, currentConversation.getMessages(), new AIActionListener() {
            @Override
            public void onAiRequestStarted() {
                runOnUiThread(() -> {
                    binding.buttonSend.setEnabled(false);
                    // Create a placeholder AI message for streaming updates
                    streamingMessage = new ChatMessage(
                        UUID.randomUUID().toString(),
                        "",
                        ChatMessage.SENDER_AI,
                        selectedModel
                    );
                    streamingMessage.setStreaming(true);
                    currentConversation.addMessage(streamingMessage);
                    streamingMessagePosition = currentConversation.getMessages().size() - 1;
                    updateMessagesUI();
                    conversationManager.saveConversation(currentConversation);
                });
            }
            
            @Override
            public void onAiRequestCompleted() {
                runOnUiThread(() -> {
                    binding.buttonSend.setEnabled(true);
                });
            }
            
            @Override
            public void onAiStreamUpdate(String partialResponse, boolean isFinal) {
                runOnUiThread(() -> {
                    if (streamingMessage != null) {
                        streamingMessage.setContent(partialResponse);
                        streamingMessage.setStreaming(!isFinal);
                        if (streamingMessagePosition >= 0) {

                            LinearLayoutManager layoutManager = (LinearLayoutManager) binding.recyclerChatMessages.getLayoutManager();
                            boolean isAtBottom = false;
                            if (layoutManager != null) {
                                int lastVisible = layoutManager.findLastCompletelyVisibleItemPosition();
                                int totalCount = messageAdapter.getItemCount();
                                if (lastVisible >= totalCount - 2) {
                                    isAtBottom = true;
                                }
                            }

                            messageAdapter.notifyItemChanged(streamingMessagePosition, "STREAM_UPDATE");

                            if (isAtBottom) {
                                binding.recyclerChatMessages.scrollToPosition(messageAdapter.getItemCount() - 1);
                            }
                        } else {
                            messageAdapter.notifyDataSetChanged();
                        }
                    }
                });
            }
            
            @Override
            public void onAiResponseComplete(String fullResponse, String modelName) {
                runOnUiThread(() -> {
                    if (streamingMessage != null) {
                        streamingMessage.setContent(fullResponse);
                        streamingMessage.setModelName(modelName);
                        streamingMessage.setStreaming(false);
                        if (streamingMessagePosition >= 0) {
                            messageAdapter.notifyItemChanged(streamingMessagePosition);
                        } else {
                            messageAdapter.notifyDataSetChanged();
                        }
                        conversationManager.saveConversation(currentConversation);
                        // Reset streaming state
                        streamingMessage = null;
                        streamingMessagePosition = -1;
                    }
                    
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
        binding.toolbar.setTitle(title);
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
        
        // Populate RadioGroup dynamically
        modelBinding.radioGroupModels.removeAllViews();
        AIModel currentModel = aiManager.getSelectedModel();
        String currentModelId = currentModel != null ? currentModel.getModelId() : null;
        int precheckedId = -1;
        for (int i = 0; i < models.size(); i++) {
            AIModel m = models.get(i);
            MaterialRadioButton rb = new MaterialRadioButton(this);
            rb.setId(View.generateViewId());
            rb.setText(cleanModelName(m.getDisplayName()));
            rb.setTag(i);
            // Match layout style
            rb.setLayoutParams(new RadioGroup.LayoutParams(
                    RadioGroup.LayoutParams.MATCH_PARENT,
                    RadioGroup.LayoutParams.WRAP_CONTENT
            ));
            rb.setPadding(rb.getPaddingLeft() + 12, rb.getPaddingTop() + 12, rb.getPaddingRight() + 12, rb.getPaddingBottom() + 12);
            modelBinding.radioGroupModels.addView(rb);
            if (currentModelId != null && currentModelId.equals(m.getModelId())) {
                precheckedId = rb.getId();
            }
        }
        if (precheckedId != -1) {
            modelBinding.radioGroupModels.check(precheckedId);
        }
        
        // Build and show once, keep reference for proper dismiss
        dialog.setView(modelBinding.getRoot());
        final androidx.appcompat.app.AlertDialog shown = dialog.create();
        shown.show();

        modelBinding.buttonCancel.setOnClickListener(v -> shown.dismiss());
        modelBinding.buttonSelect.setOnClickListener(v -> {
            int checkedId = modelBinding.radioGroupModels.getCheckedRadioButtonId();
            if (checkedId != -1) {
                View checked = modelBinding.radioGroupModels.findViewById(checkedId);
                Object tag = checked != null ? checked.getTag() : null;
                if (tag instanceof Integer) {
                    AIModel newModel = models.get((int) tag);
                    aiManager.setSelectedModel(newModel);
                    selectedModel = newModel.getDisplayName();
                    binding.textSelectedModel.setText(cleanModelName(selectedModel));
                }
            }
            shown.dismiss();
        });

        modelBinding.buttonCloseDialog.setOnClickListener(v -> shown.dismiss());
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
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setText(currentConversation.getTitle());
        input.setSelection(input.getText().length());
        input.setSingleLine(true);
        new MaterialAlertDialogBuilder(this)
            .setTitle("Rename Conversation")
            .setView(input)
            .setPositiveButton("Save", (d, w) -> {
                String newTitle = input.getText().toString().trim();
                if (!newTitle.isEmpty()) {
                    currentConversation.setTitle(newTitle);
                    binding.toolbar.setTitle(newTitle);
                    conversationManager.saveConversation(currentConversation);
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
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

    private String cleanModelName(String modelName) {
        if (modelName == null) return "";
        // Simple cleaning, can be expanded later
        return modelName.replace(" (gated)", "").trim();
    }
}