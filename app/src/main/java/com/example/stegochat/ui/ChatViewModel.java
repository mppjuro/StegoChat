package com.example.stegochat.ui;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.example.stegochat.StegoApplication;
import com.example.stegochat.db.AppDatabase;
import com.example.stegochat.db.ChatMessage;
import com.example.stegochat.repository.ChatRepository;

import java.util.List;

public class ChatViewModel extends AndroidViewModel {

    private final ChatRepository repository;

    // Identyfikator aktualnie otwartej rozmowy
    private final String currentConversationId = "default_conversation";

    public ChatViewModel(@NonNull Application application) {
        super(application);
        AppDatabase db = ((StegoApplication) application).getDatabase();
        repository = new ChatRepository(db);
    }

    /**
     * Dostarcza widokowi automatycznie odświeżającą się listę wiadomości.
     */
    public LiveData<List<ChatMessage>> getChatHistory() {
        return repository.getMessages(currentConversationId);
    }

    /**
     * Przekazuje tekst z pola EditText do warstwy sieciowo-kryptograficznej.
     */
    public void sendMessage(String text) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }

        String matrixToken = "mct_9EdOHRAQ9PAEucY8YmXUtMhDDoDQKN_nDZD13";
        String matrixRoomId = "!PhcUBJdMvnzrXbIrFe:matrix.org";
        long channelSeed = 12345L;

        // DO TESTÓW: Pobieramy nasz własny klucz, by móc odszyfrować swoje wiadomości
        java.security.PublicKey testKey = null;
        try {
            testKey = com.example.stegochat.crypto.CryptoEngine.getMyPublicKey();
        } catch (Exception e) {
            e.printStackTrace();
        }

        repository.sendMessage(
                text.trim(),
                currentConversationId,
                testKey, // Zamiast null przekazujemy własny klucz testowy
                matrixRoomId,
                matrixToken,
                channelSeed
        ).thenAccept(isSuccess -> {
            if (!isSuccess) {
                android.util.Log.e("ChatViewModel", "Błąd wysyłania wiadomości (np. sieć lub krypto)!");
            }
        });
    }
}