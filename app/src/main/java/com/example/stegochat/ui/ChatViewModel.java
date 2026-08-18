package com.example.stegochat.ui;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.example.stegochat.StegoApplication;
import com.example.stegochat.crypto.CryptoEngine;
import com.example.stegochat.db.AppDatabase;
import com.example.stegochat.db.ChatMessage;
import com.example.stegochat.repository.ChatRepository;

import java.security.PublicKey;
import java.util.List;

public class ChatViewModel extends AndroidViewModel {

    private final ChatRepository repository;
    private final SharedPreferences prefs;
    private String currentConversationId;

    public ChatViewModel(@NonNull Application application) {
        super(application);
        AppDatabase db = ((StegoApplication) application).getDatabase();
        repository = new ChatRepository(db);

        prefs = application.getSharedPreferences("stego_prefs", Context.MODE_PRIVATE);
        // Domyślnie otwórz "self_conversation" lub ostatnio zapisaną
        currentConversationId = prefs.getString("last_conv_id", "self_conversation");
    }

    public void setConversationId(String convId) {
        this.currentConversationId = convId;
        prefs.edit().putString("last_conv_id", convId).apply();
    }

    public LiveData<List<ChatMessage>> getChatHistory() {
        return repository.getMessages(currentConversationId);
    }

    public void sendMessage(String text) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }

        String matrixToken = "mct_9EdOHRAQ9PAEucY8YmXUtMhDDoDQKN_nDZD13";
        String matrixRoomId = "!PhcUBJdMvnzrXbIrFe:matrix.org";
        long channelSeed = 12345L;

        PublicKey testKey = null;
        try {
            testKey = CryptoEngine.getMyPublicKey();
        } catch (Exception e) {
            e.printStackTrace();
        }

        repository.sendMessage(
                text.trim(),
                currentConversationId,
                testKey,
                matrixRoomId,
                matrixToken,
                channelSeed
        );
    }
}