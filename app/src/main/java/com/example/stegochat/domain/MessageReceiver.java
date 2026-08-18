package com.example.stegochat.domain;

import android.graphics.Bitmap;
import android.util.Log;

import com.example.stegochat.crypto.CryptoEngine;
import com.example.stegochat.crypto.StegoEngine;
import com.example.stegochat.db.AppDatabase;
import com.example.stegochat.db.ChatMessage;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.Signature;
import java.util.concurrent.CompletableFuture;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public class MessageReceiver {

    private static final String TAG = "MessageReceiver";

    /**
     * Główna funkcja procesująca wszystkie przychodzące obrazki.
     */
    public static CompletableFuture<Boolean> processIncomingImage(
            Bitmap stegoImage,
            long channelPrngSeed,
            PublicKey senderPublicKey,
            String expectedConversationId,
            AppDatabase db) {

        return CompletableFuture.supplyAsync(() -> {
            try {
                // 1. Ekstrakcja surowych bitów (steganografia odwrotna)
                byte[] extractedBinary = StegoEngine.extractData(stegoImage, channelPrngSeed);

                // 2. Parsowanie binarnego payloadu (rozklejanie IV, kluczy, podpisów)
                StegoPayload payload = StegoPayload.fromBytes(extractedBinary);

                // 3. Weryfikacja podpisu cyfrowego (RSA-PSS)
                // (Gwarantuje, że to nie "Cover Traffic", a faktyczna wiadomość od konkretnego nadawcy)
                if (senderPublicKey != null) {
                    Signature sig = Signature.getInstance("SHA256withRSA/PSS");
                    sig.initVerify(senderPublicKey);
                    sig.update(payload.ciphertext);
                    if (!sig.verify(payload.signature)) {
                        throw new Exception("Błąd weryfikacji podpisu! Wiadomość sfałszowana.");
                    }
                }

                // 4. Kapsułkowanie odwrotne (deszyfrowanie klucza AES naszym kluczem prywatnym z Keystore)
                byte[] sessionKeyBytes = CryptoEngine.decapsulateSessionKey(payload.encryptedSessionKey);
                SecretKey sessionKey = new SecretKeySpec(sessionKeyBytes, 0, sessionKeyBytes.length, "AES");

                // 5. Odszyfrowanie GCM (zdjęcie warstwy AES-256)
                byte[] paddedGzipBytes = CryptoEngine.decryptAESGCM(payload.ciphertext, sessionKey, payload.iv);

                // 6. Dekompresja GZIP
                // GZIPInputStream jest na tyle inteligentny, że ignoruje dopięty "Padding"
                // (kryptograficzny szum) znajdujący się na końcu bufora, czytając tylko czysty JSON.
                byte[] jsonBytes = StegoEngine.decompressGzip(paddedGzipBytes);
                String jsonString = new String(jsonBytes, StandardCharsets.UTF_8);

                // 7. Parsowanie zdeserializowanego JSONa z metadanymi
                JsonObject internalPayload = new Gson().fromJson(jsonString, JsonObject.class);
                String msgId = internalPayload.get("id").getAsString();
                long timestamp = internalPayload.get("t").getAsLong();
                String text = internalPayload.get("msg").getAsString();

                // 8. Zapis do bazy danych
                ChatMessage chatMessage = new ChatMessage(msgId);
                chatMessage.conversationId = expectedConversationId;
                chatMessage.timestamp = timestamp;
                chatMessage.plaintext = text;
                chatMessage.isOutgoing = false;
                chatMessage.status = 2; // 2 = DELIVERED

                db.chatDao().insertMessage(chatMessage);
                Log.d(TAG, "Pomyślnie odebrano i odszyfrowano wiadomość: " + msgId);

                return true;

            } catch (Exception e) {
                // Ciche odrzucenie - BARDZO WAŻNE w tym systemie.
                // Rzucenie wyjątku oznacza po prostu, że ten konkretny mem był:
                // a) Czystym memem (bez magicznego nagłówka STEG).
                // b) Sztucznym ruchem generowanym przez inny węzeł (Cover Traffic).
                // c) Wiadomością, ale zaszyfrowaną kluczem dla innej osoby.
                Log.v(TAG, "Ignorowanie obrazka: " + e.getMessage());
                return false;
            }
        });
    }
}