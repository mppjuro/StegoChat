package com.example.stegochat.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.lifecycle.LiveData;

import java.util.List;

@Dao
public interface ChatDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertMessage(ChatMessage message);

    @Query("SELECT * FROM messages WHERE conversationId = :convId ORDER BY timestamp ASC")
    LiveData<List<ChatMessage>> getMessagesForConversation(String convId);

    @Query("UPDATE messages SET status = :newStatus WHERE messageId = :msgId")
    void updateMessageStatus(String msgId, int newStatus);

    @Query("SELECT * FROM messages WHERE status = 0 ORDER BY timestamp ASC LIMIT 1")
    ChatMessage getNextPendingMessage();
}