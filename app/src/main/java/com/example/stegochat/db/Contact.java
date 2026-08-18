package com.example.stegochat.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "contacts")
public class Contact {
    @PrimaryKey
    @NonNull
    public String pubKeyBase64; // Klucz publiczny jest unikalnym ID użytkownika

    public String name;
    public String conversationId; // Identyfikator UUID rozmowy do filtrowania czatu

    public Contact(@NonNull String pubKeyBase64) {
        this.pubKeyBase64 = pubKeyBase64;
    }
}