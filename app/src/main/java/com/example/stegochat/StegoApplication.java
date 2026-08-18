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
            // Jeśli klucz już istnieje w TEE urządzenia, metoda nic nie zrobi.
            CryptoEngine.generateIdentityKeyIfNotExists();
            Log.d(TAG, "Sprzętowy klucz tożsamości gotowy.");

            // 2. Inicjalizacja bazy danych (SQLCipher)
            // W pełnej wersji aplikacji to hasło powinno być generowane z PIN-u
            // lub funkcji biometrycznej za pomocą funkcji hashującej (np. Argon2/PBKDF2).
            // Do celów projektowych/testowych używamy stałej soli.
            byte[] dbPassphrase = "SuperSecretDevPassword2026!".getBytes();
            database = AppDatabase.getDatabase(this, dbPassphrase);
            Log.d(TAG, "Baza danych (Room + SQLCipher) zamontowana.");

        } catch (Exception e) {
            Log.e(TAG, "Krytyczny błąd inicjalizacji kryptografii!", e);
        }
    }

    public AppDatabase getDatabase() {
        return database;
    }
}