package com.example.stegochat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
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
import com.example.stegochat.service.StegoBackgroundService;
import com.example.stegochat.ui.ChatAdapter;
import com.example.stegochat.ui.ChatViewModel;
import com.example.stegochat.ui.ContactsActivity;
import com.example.stegochat.ui.QrScanActivity;

public class MainActivity extends AppCompatActivity {

    private ChatViewModel chatViewModel;
    private ChatAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Konfiguracja własnego Toolbara (górnej belki)
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Zapytanie o uprawnienia do powiadomień (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }

        // Inicjalizacja elementów widoku
        RecyclerView recyclerView = findViewById(R.id.chatRecyclerView);
        EditText messageInput = findViewById(R.id.messageEditText);
        Button sendButton = findViewById(R.id.sendButton);

        adapter = new ChatAdapter();
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);

        // Podłączenie ViewModelu
        chatViewModel = new ViewModelProvider(this).get(ChatViewModel.class);

        // Obserwowanie historii konwersacji z bazy danych
        chatViewModel.getChatHistory().observe(this, messages -> {
            adapter.setMessages(messages);
            if (messages.size() > 0) {
                recyclerView.smoothScrollToPosition(messages.size() - 1);
            }
        });

        // Obsługa wysyłania wiadomości
        sendButton.setOnClickListener(v -> {
            String text = messageInput.getText().toString();
            if (!text.isEmpty()) {
                chatViewModel.sendMessage(text);
                messageInput.setText("");
            }
        });

        // Uruchomienie usługi w tle (nasłuch + Cover Traffic)
        startStegoService();
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
                    // Wyskakujący debug do testów z dwoma telefonami
                    //android.widget.Toast.makeText(this, "Otwarty czat: " + finalTitle + "\nID: " + activeId, android.widget.Toast.LENGTH_SHORT).show();
                });
            }).start();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Wypełnienie górnego paska ikonami z pliku main_menu.xml
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem themeItem = menu.findItem(R.id.action_theme_toggle);
        if (themeItem != null) {
            int currentNightMode = getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
            if (currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES) {
                themeItem.setTitle("☀️"); // Słońce w trybie ciemnym
            } else {
                themeItem.setTitle("🌙"); // Księżyc w trybie jasnym
            }
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_theme_toggle) {
            int currentNightMode = getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
            if (currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES) {
                androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);
            } else {
                androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES);
            }
            return true;
        } else if (itemId == R.id.action_add_contact) {
            startActivity(new Intent(this, com.example.stegochat.ui.QrScanActivity.class));
            return true;
        } else if (itemId == R.id.action_contacts) {
            startActivity(new Intent(this, com.example.stegochat.ui.ContactsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    private void startStegoService() {
        Intent serviceIntent = new Intent(this, StegoBackgroundService.class);
        ContextCompat.startForegroundService(this, serviceIntent);
    }
}