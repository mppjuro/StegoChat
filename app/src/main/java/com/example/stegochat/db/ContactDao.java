package com.example.stegochat.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface ContactDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertContact(Contact contact);

    @Query("SELECT * FROM contacts")
    LiveData<List<Contact>> getAllContacts();

    @Query("UPDATE contacts SET name = :newName WHERE pubKeyBase64 = :key")
    void updateContactName(String key, String newName);

    @Query("SELECT * FROM contacts WHERE pubKeyBase64 = :key LIMIT 1")
    Contact getContactByKey(String key);

    @Query("SELECT * FROM contacts WHERE conversationId = :convId LIMIT 1")
    Contact getContactByConversationId(String convId);
}