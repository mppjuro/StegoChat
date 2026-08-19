package com.example.stegochat.ui;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

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
    private final MutableLiveData<String> currentConversationIdLive = new MutableLiveData<>();
    private final LiveData<List<ChatMessage>> chatHistory;

    public ChatViewModel(@NonNull Application application) {
        super(application);
        AppDatabase db = ((StegoApplication) application).getDatabase();
        repository = new ChatRepository(db);

        prefs = application.getSharedPreferences("stego_prefs", Context.MODE_PRIVATE);
        String savedConvId = prefs.getString("last_conv_id", "self_conversation");
        currentConversationIdLive.setValue(savedConvId);

        // Używamy SwitchMap, aby LiveData automatycznie przełączało zapytanie do bazy po zmianie ID konwersacji
        chatHistory = Transformations.switchMap(currentConversationIdLive, repository::getMessages);
    }

    public void setConversationId(String convId) {
        prefs.edit().putString("last_conv_id", convId).apply();
        currentConversationIdLive.setValue(convId);
    }

    public String getCurrentConversationId() {
        return currentConversationIdLive.getValue();
    }

    public LiveData<List<ChatMessage>> getChatHistory() {
        return chatHistory;
    }

    public void sendMessage(String text) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }

        String matrixToken = "mct_9EdOHRAQ9PAEucY8YmXUtMhDDoDQKN_nDZD13";
        String matrixRoomId = "!PhcUBJdMvnzrXbIrFe:matrix.org";
        long channelSeed = 12345L;
        String activeConvId = currentConversationIdLive.getValue();

        // Operacja na bazie musi iść w tle
        new Thread(() -> {
            AppDatabase db = ((StegoApplication) getApplication()).getDatabase();
            PublicKey recipientKey = null;

            try {
                if ("self_conversation".equals(activeConvId)) {
                    recipientKey = CryptoEngine.getMyPublicKey();
                } else {
                    com.example.stegochat.db.Contact contact = db.contactDao().getContactByConversationId(activeConvId);
                    if (contact != null) {
                        recipientKey = CryptoEngine.decodePublicKey(contact.pubKeyBase64);
                    } else {
                        recipientKey = CryptoEngine.getMyPublicKey(); // Fallback
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            if (recipientKey != null) {
                repository.sendMessage(text.trim(), activeConvId, recipientKey, matrixRoomId, matrixToken, channelSeed);
            }
        }).start();
    }
}