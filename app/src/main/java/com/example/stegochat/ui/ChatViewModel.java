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

        // Poniższe parametry w produkcyjnej aplikacji powinny być ładowane
        // z konfiguracji powiązanej z danym "currentConversationId"
        String matrixToken = "TWÓJ_TOKEN_MATRIX";
        String matrixRoomId = "!twójRoomId:matrix.org";
        long channelSeed = 12345L;

        repository.sendMessage(
                text.trim(),
                currentConversationId,
                null, // W wersji finalnej podaj tu PublicKey partnera
                matrixRoomId,
                matrixToken,
                channelSeed
        ).thenAccept(isSuccess -> {
            // Ze względu na CompletableFuture, jesteśmy tutaj w tle.
            // Opcjonalnie można tu dodać postowanie błędów do LiveData (np. Snackbar w UI)
            if (!isSuccess) {
                // Obsługa błędu wysyłania (np. powiadomienie użytkownika)
            }
        });
    }
}