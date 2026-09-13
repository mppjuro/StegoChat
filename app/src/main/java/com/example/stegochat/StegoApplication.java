package com.example.stegochat;

import android.app.Application;
import android.util.Log;

import com.example.stegochat.crypto.CryptoEngine;
import com.example.stegochat.db.AppDatabase;

public class StegoApplication extends Application {

    private static final String TAG = "StegoApplication";
    private AppDatabase database;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Inicjalizacja środowiska StegoChat...");

        try {
            // 1. Inicjalizacja sprzętowego klucza tożsamości (RSA-4096)
            CryptoEngine.generateIdentityKeyIfNotExists();
            Log.d(TAG, "Sprzętowy klucz tożsamości gotowy.");

            // 2. Inicjalizacja bazy danych (SQLCipher) z wykorzystaniem Android Keystore
            byte[] dbPassphrase = CryptoEngine.getOrGenerateDbKey();
            database = AppDatabase.getDatabase(this, dbPassphrase);
            Log.d(TAG, "Baza danych (Room + SQLCipher) zamontowana z kluczem sprzętowym.");

        } catch (Exception e) {
            Log.e(TAG, "Krytyczny błąd inicjalizacji kryptografii!", e);
        }
    }

    public AppDatabase getDatabase() {
        return database;
    }
}