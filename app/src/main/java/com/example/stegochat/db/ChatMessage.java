package com.example.stegochat.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "messages")
public class ChatMessage {

    @PrimaryKey
    @NonNull
    public String messageId; // UUID wiadomości

    public String conversationId; // Identyfikator pary (rozmówcy)
    public long timestamp;        // Czas wysłania/odbioru (UNIX epoch)
    public String plaintext;      // Czysty tekst (w bazie jest bezpieczny, bo baza używa SQLCipher)
    public boolean isOutgoing;    // true = wysłana przez nas, false = odebrana
    public int status;            // 0=PENDING (oczekuje na wysłanie jako cover traffic), 1=SENT, 2=DELIVERED

    public ChatMessage(@NonNull String messageId) {
        this.messageId = messageId;
    }
}