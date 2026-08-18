package com.example.stegochat.repository;

import androidx.lifecycle.LiveData;

import com.example.stegochat.db.AppDatabase;
import com.example.stegochat.db.ChatDao;
import com.example.stegochat.db.ChatMessage;
import com.example.stegochat.domain.MessageProcessor;

import java.security.PublicKey;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ChatRepository {

    private final ChatDao chatDao;
    private final AppDatabase db;

    public ChatRepository(AppDatabase database) {
        this.db = database;
        this.chatDao = database.chatDao();
    }

    /**
     * Zwraca reaktywny strumień wiadomości dla danej konwersacji.
     */
    public LiveData<List<ChatMessage>> getMessages(String conversationId) {
        return chatDao.getMessagesForConversation(conversationId);
    }

    /**
     * Uruchamia pełen cykl steganograficznego wysyłania wiadomości.
     */
    public CompletableFuture<Boolean> sendMessage(
            String text,
            String conversationId,
            PublicKey recipientPublicKey,
            String matrixRoomId,
            String matrixToken,
            long channelPrngSeed) {

        // Delegujemy ciężką pracę do domeny (kompresja, kryptografia, steganografia, sieć)
        return MessageProcessor.processAndSendMessage(
                text,
                conversationId,
                recipientPublicKey,
                matrixRoomId,
                matrixToken,
                channelPrngSeed,
                db
        );
    }
}