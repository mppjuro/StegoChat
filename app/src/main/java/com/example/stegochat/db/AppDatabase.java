package com.example.stegochat.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import net.sqlcipher.database.SupportFactory;

@Database(entities = {ChatMessage.class, Contact.class}, version = 5, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    public abstract ChatDao chatDao();
    public abstract ContactDao contactDao();

    private static volatile AppDatabase INSTANCE;

    public static AppDatabase getDatabase(final Context context, byte[] passphrase) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    // Inicjalizacja fabryki SQLCipher z hasłem (kluczem)
                    SupportFactory factory = new SupportFactory(passphrase);

                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "stegochat_encrypted.db")
                            .openHelperFactory(factory)
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}