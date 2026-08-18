package com.example.stegochat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.stegochat.service.StegoBackgroundService;
import com.example.stegochat.ui.ChatAdapter;
import com.example.stegochat.ui.ChatViewModel;

public class MainActivity extends AppCompatActivity {

    private ChatViewModel chatViewModel;
    private ChatAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Zapytanie o uprawnienia do powiadomień (wymagane w Android 13+ dla Foreground Service)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }

        // Inicjalizacja UI
        RecyclerView recyclerView = findViewById(R.id.chatRecyclerView);
        EditText messageInput = findViewById(R.id.messageEditText);
        Button sendButton = findViewById(R.id.sendButton);

        adapter = new ChatAdapter();
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        // Najnowsze wiadomości na dole
        layoutManager.setStackFromEnd(true);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);

        // Podłączenie ViewModelu
        chatViewModel = new ViewModelProvider(this).get(ChatViewModel.class);

        // Obserwowanie zmian w bazie danych
        chatViewModel.getChatHistory().observe(this, messages -> {
            adapter.setMessages(messages);
            if (messages.size() > 0) {
                recyclerView.smoothScrollToPosition(messages.size() - 1);
            }
        });

        // Wysyłanie nowej wiadomości
        sendButton.setOnClickListener(v -> {
            String text = messageInput.getText().toString();
            if (!text.isEmpty()) {
                chatViewModel.sendMessage(text);
                messageInput.setText("");
            }
        });

        // Uruchomienie usługi w tle (nasłuch Matrixa + Cover Traffic)
        startStegoService();
    }

    private void startStegoService() {
        Intent serviceIntent = new Intent(this, StegoBackgroundService.class);
        ContextCompat.startForegroundService(this, serviceIntent);
    }
}