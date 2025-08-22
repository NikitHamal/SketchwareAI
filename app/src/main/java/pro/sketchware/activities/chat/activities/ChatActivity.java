package pro.sketchware.activities.chat.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;

import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import pro.sketchware.R;
import pro.sketchware.activities.chat.adapters.MessagesAdapter;
import pro.sketchware.activities.chat.api.ChatApiClient;
import pro.sketchware.activities.chat.api.DeepInfraApiClient;
import pro.sketchware.activities.chat.database.ChatDatabaseHelper;
import pro.sketchware.activities.chat.models.AIModel;
import pro.sketchware.activities.chat.models.ChatConversation;
import pro.sketchware.activities.chat.models.ChatMessage;

public class ChatActivity extends AppCompatActivity implements ChatApiClient.MessageListener {
    
    private static final String EXTRA_CONVERSATION_ID = "conversation_id";
    
    // UI Components
    private ImageButton btnBack;
    private TextView tvConversationTitle;
    private ImageButton btnMoreOptions;
    private RecyclerView recyclerMessages;
    private View layoutEmptyState;
    private View layoutInputContainer;
    private TextView tvModelName;
    private ImageButton btnTuneOptions;
    private TextInputEditText etMessage;
    private FloatingActionButton fabSend;
    private View layoutLoading;
    private TextView tvLoadingText;
    
    // Data and logic
    private ChatDatabaseHelper databaseHelper;
    private ChatApiClient apiClient;
    private ExecutorService executorService;
    private MessagesAdapter messagesAdapter;
    
    private long conversationId = -1;
    private ChatConversation currentConversation;
    private AIModel currentModel;
    private boolean isThinkingEnabled = false;
    private boolean isWebSearchEnabled = false;
    private boolean isAgentEnabled = true;
    private boolean isRequestInProgress = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);
        
        // Get conversation ID from intent
        conversationId = getIntent().getLongExtra(EXTRA_CONVERSATION_ID, -1);
        if (conversationId == -1) {
            finish();
            return;
        }
        
        initializeComponents();
        initializeViews();
        setupRecyclerView();
        setupClickListeners();
        loadConversation();
    }

    private void initializeComponents() {
        databaseHelper = new ChatDatabaseHelper(this);
        apiClient = new DeepInfraApiClient(this);
        executorService = Executors.newSingleThreadExecutor();
        
        // Initialize with default model
        List<AIModel> models = apiClient.getAvailableModels();
        if (!models.isEmpty()) {
            currentModel = models.get(0);
        }
    }

    private void initializeViews() {
        btnBack = findViewById(R.id.btn_back);
        tvConversationTitle = findViewById(R.id.tv_conversation_title);
        btnMoreOptions = findViewById(R.id.btn_more_options);
        recyclerMessages = findViewById(R.id.recycler_messages);
        layoutEmptyState = findViewById(R.id.layout_empty_state);
        layoutInputContainer = findViewById(R.id.layout_input_container);
        tvModelName = findViewById(R.id.tv_model_name);
        btnTuneOptions = findViewById(R.id.btn_tune_options);
        etMessage = findViewById(R.id.et_message);
        fabSend = findViewById(R.id.fab_send);
        layoutLoading = findViewById(R.id.layout_loading);
        tvLoadingText = findViewById(R.id.tv_loading_text);
    }

    private void setupRecyclerView() {
        messagesAdapter = new MessagesAdapter(this);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        
        recyclerMessages.setLayoutManager(layoutManager);
        recyclerMessages.setAdapter(messagesAdapter);
    }

    private void setupClickListeners() {
        btnBack.setOnClickListener(v -> onBackPressed());
        
        btnMoreOptions.setOnClickListener(v -> showMoreOptionsMenu());
        
        tvModelName.setOnClickListener(v -> showModelSelectionDialog());
        
        btnTuneOptions.setOnClickListener(v -> showAgentOptionsBottomSheet());
        
        fabSend.setOnClickListener(v -> sendMessage());
        
        etMessage.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage();
                return true;
            }
            return false;
        });
        
        etMessage.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                boolean hasText = !TextUtils.isEmpty(s.toString().trim());
                fabSend.setEnabled(hasText && !isRequestInProgress);
            }
        });
    }

    private void loadConversation() {
        executorService.execute(() -> {
            currentConversation = databaseHelper.getConversation(conversationId);
            List<ChatMessage> messages = databaseHelper.getMessagesForConversation(conversationId);
            
            runOnUiThread(() -> {
                if (currentConversation != null) {
                    updateUI();
                    messagesAdapter.setMessages(messages);
                    updateEmptyState(messages.isEmpty());
                    
                    // Update model and settings from conversation
                    if (currentConversation.getModelName() != null) {
                        tvModelName.setText(currentConversation.getModelName());
                    }
                    isThinkingEnabled = currentConversation.isThinkingEnabled();
                    isWebSearchEnabled = currentConversation.isWebSearchEnabled();
                    isAgentEnabled = currentConversation.isAgentEnabled();
                }
            });
        });
    }

    private void updateUI() {
        if (currentConversation != null) {
            String title = currentConversation.getTitle();
            if (title == null || title.trim().isEmpty()) {
                title = "New Conversation";
            }
            tvConversationTitle.setText(title);
        }
    }

    private void updateEmptyState(boolean isEmpty) {
        if (isEmpty) {
            recyclerMessages.setVisibility(View.GONE);
            layoutEmptyState.setVisibility(View.VISIBLE);
        } else {
            recyclerMessages.setVisibility(View.VISIBLE);
            layoutEmptyState.setVisibility(View.GONE);
        }
    }

    private void sendMessage() {
        String messageText = etMessage.getText().toString().trim();
        if (TextUtils.isEmpty(messageText) || isRequestInProgress) {
            return;
        }
        
        // Clear input
        etMessage.setText("");
        
        // Create and save user message
        ChatMessage userMessage = new ChatMessage(conversationId, ChatMessage.SENDER_USER, messageText);
        saveAndDisplayMessage(userMessage);
        
        // Update conversation title if it's still "New Conversation"
        if (currentConversation != null && 
            ("New Conversation".equals(currentConversation.getTitle()) || 
             currentConversation.getTitle() == null)) {
            String newTitle = generateConversationTitle(messageText);
            currentConversation.setTitle(newTitle);
            tvConversationTitle.setText(newTitle);
            updateConversation();
        }
        
        // Send to AI
        List<ChatMessage> history = databaseHelper.getMessagesForConversation(conversationId);
        apiClient.sendMessage(
            messageText, 
            currentModel, 
            history, 
            isThinkingEnabled, 
            isWebSearchEnabled, 
            isAgentEnabled, 
            this
        );
        
        updateEmptyState(false);
    }

    private String generateConversationTitle(String firstMessage) {
        // Generate a simple title from the first message
        if (firstMessage.length() > 30) {
            return firstMessage.substring(0, 27) + "...";
        }
        return firstMessage;
    }

    private void saveAndDisplayMessage(ChatMessage message) {
        executorService.execute(() -> {
            databaseHelper.insertMessage(message);
            
            runOnUiThread(() -> {
                messagesAdapter.addMessage(message);
                scrollToBottom();
            });
        });
    }

    private void updateConversation() {
        if (currentConversation != null) {
            executorService.execute(() -> {
                databaseHelper.updateConversation(currentConversation);
            });
        }
    }

    private void scrollToBottom() {
        if (messagesAdapter.getItemCount() > 0) {
            recyclerMessages.smoothScrollToPosition(messagesAdapter.getItemCount() - 1);
        }
    }

    private void showMoreOptionsMenu() {
        // TODO: Implement more options menu
        new MaterialAlertDialogBuilder(this)
                .setTitle("More Options")
                .setMessage("Additional options will be implemented here.")
                .setPositiveButton("OK", null)
                .show();
    }

    private void showModelSelectionDialog() {
        List<AIModel> models = apiClient.getAvailableModels();
        String[] modelNames = new String[models.size()];
        
        for (int i = 0; i < models.size(); i++) {
            modelNames[i] = models.get(i).getDisplayName();
        }
        
        int currentSelection = -1;
        if (currentModel != null) {
            for (int i = 0; i < models.size(); i++) {
                if (models.get(i).getModelId().equals(currentModel.getModelId())) {
                    currentSelection = i;
                    break;
                }
            }
        }
        
        new MaterialAlertDialogBuilder(this)
                .setTitle("Select AI Model")
                .setSingleChoiceItems(modelNames, currentSelection, (dialog, which) -> {
                    currentModel = models.get(which);
                    tvModelName.setText(currentModel.getDisplayName());
                    
                    // Update conversation model
                    if (currentConversation != null) {
                        currentConversation.setModelName(currentModel.getDisplayName());
                        updateConversation();
                    }
                    
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showAgentOptionsBottomSheet() {
        BottomSheetDialog bottomSheet = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_agent_options, null);
        bottomSheet.setContentView(view);
        
        MaterialSwitch switchThinking = view.findViewById(R.id.switch_thinking);
        MaterialSwitch switchWebSearch = view.findViewById(R.id.switch_web_search);
        MaterialSwitch switchAgent = view.findViewById(R.id.switch_agent);
        
        // Set current states
        switchThinking.setChecked(isThinkingEnabled);
        switchWebSearch.setChecked(isWebSearchEnabled);
        switchAgent.setChecked(isAgentEnabled);
        
        // Update switches based on current model capabilities
        if (currentModel != null) {
            switchThinking.setEnabled(currentModel.supportsThinking());
            switchWebSearch.setEnabled(currentModel.supportsWebSearch());
        }
        
        // Set listeners
        switchThinking.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isThinkingEnabled = isChecked;
            updateConversationSettings();
        });
        
        switchWebSearch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isWebSearchEnabled = isChecked;
            updateConversationSettings();
        });
        
        switchAgent.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isAgentEnabled = isChecked;
            updateConversationSettings();
        });
        
        bottomSheet.show();
    }

    private void updateConversationSettings() {
        if (currentConversation != null) {
            currentConversation.setThinkingEnabled(isThinkingEnabled);
            currentConversation.setWebSearchEnabled(isWebSearchEnabled);
            currentConversation.setAgentEnabled(isAgentEnabled);
            updateConversation();
        }
    }

    // ChatApiClient.MessageListener implementation
    @Override
    public void onMessageStart() {
        runOnUiThread(() -> {
            isRequestInProgress = true;
            fabSend.setEnabled(false);
            layoutLoading.setVisibility(View.VISIBLE);
            tvLoadingText.setText("Thinking...");
        });
    }

    @Override
    public void onMessageChunk(String chunk, boolean isComplete) {
        // For streaming updates, we'll update the last message if it's from AI
        // This would require more complex logic to handle partial updates
    }

    @Override
    public void onMessageComplete(String fullMessage, String modelName) {
        runOnUiThread(() -> {
            ChatMessage aiMessage = new ChatMessage(conversationId, ChatMessage.SENDER_AI, fullMessage, modelName);
            saveAndDisplayMessage(aiMessage);
            
            // Update conversation last message
            if (currentConversation != null) {
                currentConversation.setLastMessage(fullMessage.length() > 50 ? 
                    fullMessage.substring(0, 47) + "..." : fullMessage);
                currentConversation.setLastMessageTime(new Date());
                updateConversation();
            }
        });
    }

    @Override
    public void onError(String error) {
        runOnUiThread(() -> {
            ChatMessage errorMessage = new ChatMessage(conversationId, ChatMessage.SENDER_AI, 
                "Sorry, I encountered an error: " + error, "SketchAI");
            saveAndDisplayMessage(errorMessage);
        });
    }

    @Override
    public void onRequestCompleted() {
        runOnUiThread(() -> {
            isRequestInProgress = false;
            fabSend.setEnabled(!TextUtils.isEmpty(etMessage.getText().toString().trim()));
            layoutLoading.setVisibility(View.GONE);
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
        if (databaseHelper != null) {
            databaseHelper.close();
        }
    }

    // Helper class for text watching
    private static abstract class SimpleTextWatcher implements android.text.TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override
        public void afterTextChanged(android.text.Editable s) {}
    }
}