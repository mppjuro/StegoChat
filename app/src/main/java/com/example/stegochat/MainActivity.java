package com.example.stegochat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.stegochat.db.AppDatabase;
import com.example.stegochat.domain.MessageProcessor;
import com.example.stegochat.network.NetworkOrchestrator;
import com.example.stegochat.service.StegoBackgroundService;
import com.example.stegochat.ui.ChatAdapter;
import com.example.stegochat.ui.ChatViewModel;
import com.example.stegochat.ui.ContactsActivity;
import com.example.stegochat.ui.QrScanActivity;
import com.google.android.material.snackbar.Snackbar;

public class MainActivity extends AppCompatActivity {

    private ChatViewModel chatViewModel;
    private ChatAdapter adapter;
    private EditText messageInput;
    private Button sendButton;

    // Przechowywanie pobranego mema i jego faktycznej pojemności
    private byte[] preFetchedMeme = null;
    private int currentMemeCapacity = MessageProcessor.MAX_LSB_CAPACITY_BYTES;
    private boolean isFetchingMeme = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }

        RecyclerView recyclerView = findViewById(R.id.chatRecyclerView);
        messageInput = findViewById(R.id.messageEditText);
        sendButton = findViewById(R.id.sendButton);

        adapter = new ChatAdapter();
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);

        chatViewModel = new ViewModelProvider(this).get(ChatViewModel.class);
        chatViewModel.getChatHistory().observe(this, messages -> {
            adapter.setMessages(messages);
            if (messages.size() > 0) {
                recyclerView.smoothScrollToPosition(messages.size() - 1);
            }
        });

        // Nasłuch na żywo
        sendButton.setEnabled(false);
        messageInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String text = s.toString();

                if (!text.trim().isEmpty() && preFetchedMeme == null && !isFetchingMeme) {
                    isFetchingMeme = true;
                    validateMessageSize(text); // Pokazuje "pobieram mema"

                    NetworkOrchestrator.fetchRandomMemeBytes().thenAccept(bytes -> {
                        runOnUiThread(() -> {
                            preFetchedMeme = bytes;
                            isFetchingMeme = false;
                            currentMemeCapacity = MessageProcessor.calculateMemeCapacityBytes(bytes);
                            validateMessageSize(messageInput.getText().toString());
                        });
                    }).exceptionally(ex -> {
                        runOnUiThread(() -> {
                            isFetchingMeme = false;
                            validateMessageSize(messageInput.getText().toString());
                        });
                        return null;
                    });
                } else if (text.trim().isEmpty()) {
                    // Reset jeśli użytkownik usunął cały tekst
                    preFetchedMeme = null;
                    currentMemeCapacity = MessageProcessor.MAX_LSB_CAPACITY_BYTES;
                }

                validateMessageSize(text);
            }
        });

        sendButton.setOnClickListener(v -> {
            String text = messageInput.getText().toString();
            if (!text.trim().isEmpty()) {
                int estimatedSize = MessageProcessor.calculatePayloadSize(text);
                if (estimatedSize > currentMemeCapacity) {
                    Snackbar.make(v, "Nie wysłano, wiadomość zbyt długa", Snackbar.LENGTH_LONG).show();
                    return;
                }

                chatViewModel.sendMessage(text, preFetchedMeme);

                // Reset po wysłaniu wiadomości
                messageInput.setText("");
                preFetchedMeme = null;
                currentMemeCapacity = MessageProcessor.MAX_LSB_CAPACITY_BYTES;
            }
        });

        startStegoService();

        new Thread(() -> {
            try {
                AppDatabase db = ((StegoApplication) getApplication()).getDatabase();
                String myKey = com.example.stegochat.crypto.CryptoEngine.encodePublicKey(
                        com.example.stegochat.crypto.CryptoEngine.getMyPublicKey());

                if (db.contactDao().getContactByKey(myKey) == null) {
                    com.example.stegochat.db.Contact selfContact = new com.example.stegochat.db.Contact(myKey);
                    selfContact.name = "JA (Notatnik / Sam ze sobą)";
                    selfContact.conversationId = "self_conversation";
                    db.contactDao().insertContact(selfContact);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void validateMessageSize(String text) {
        if (text.trim().isEmpty()) {
            sendButton.setEnabled(false);
            messageInput.setError(null);
            return;
        }

        int estimatedSize = MessageProcessor.calculatePayloadSize(text);

        if (preFetchedMeme == null) {
            if (isFetchingMeme) {
                sendButton.setEnabled(false);
                messageInput.setError("Pobieram nośnik steganograficzny (mema)...");
            } else {
                // Tryb awaryjny - jeśli pobieranie padło, blokujemy w oparciu o domyślną pojemność
                if (estimatedSize > MessageProcessor.MAX_LSB_CAPACITY_BYTES) {
                    sendButton.setEnabled(false);
                    messageInput.setError("Wiadomość jest zbyt długa (max ~375 kB)");
                } else {
                    sendButton.setEnabled(true);
                    messageInput.setError(null);
                }
            }
        } else {
            // Obraz pobrany - sztywna walidacja do jego rozmiaru!
            if (estimatedSize > currentMemeCapacity) {
                sendButton.setEnabled(false);
                messageInput.setError("Zbyt długa (max " + (currentMemeCapacity / 1024) + " kB dla wybranego na ten moment obrazka)");
            } else {
                sendButton.setEnabled(true);
                messageInput.setError(null);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (chatViewModel != null) {
            String activeId = getSharedPreferences("stego_prefs", MODE_PRIVATE).getString("last_conv_id", "self_conversation");
            chatViewModel.setConversationId(activeId);

            new Thread(() -> {
                AppDatabase db = ((StegoApplication) getApplication()).getDatabase();
                String title = "StegoChat (Ja)";
                if (!"self_conversation".equals(activeId)) {
                    com.example.stegochat.db.Contact contact = db.contactDao().getContactByConversationId(activeId);
                    if (contact != null && contact.name != null) {
                        title = contact.name;
                    }
                }

                final String finalTitle = title;
                runOnUiThread(() -> {
                    if (getSupportActionBar() != null) {
                        getSupportActionBar().setTitle(finalTitle);
                    }
                });
            }).start();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem themeItem = menu.findItem(R.id.action_theme_toggle);
        if (themeItem != null) {
            int currentNightMode = getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
            themeItem.setTitle(currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES ? "☀️" : "🌙");
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_theme_toggle) {
            int currentNightMode = getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                    currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES ?
                            androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO : androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES);
            return true;
        } else if (itemId == R.id.action_add_contact) {
            startActivity(new Intent(this, QrScanActivity.class));
            return true;
        } else if (itemId == R.id.action_contacts) {
            startActivity(new Intent(this, ContactsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void startStegoService() {
        Intent serviceIntent = new Intent(this, StegoBackgroundService.class);
        ContextCompat.startForegroundService(this, serviceIntent);
    }
}