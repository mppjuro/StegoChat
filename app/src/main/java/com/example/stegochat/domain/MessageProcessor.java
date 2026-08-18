package com.example.stegochat.domain;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import com.example.stegochat.crypto.CryptoEngine;
import com.example.stegochat.crypto.StegoEngine;
import com.example.stegochat.db.AppDatabase;
import com.example.stegochat.db.ChatMessage;
import com.example.stegochat.network.NetworkOrchestrator;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import javax.crypto.SecretKey;

public class MessageProcessor {

    private static final String TAG = "MessageProcessor";
    private static final int PADDING_BLOCK_SIZE = 1024; // Uzupełniamy do stałej wielkości

    /**
     * Pełen cykl wysłania wiadomości (Tx).
     */
    public static CompletableFuture<Boolean> processAndSendMessage(
            String rawText,
            String conversationId,
            PublicKey recipientPublicKey,
            String matrixRoomId,
            String matrixToken,
            long channelPrngSeed,
            AppDatabase db) {

        return CompletableFuture.supplyAsync(() -> {
            try {
                // 1. Zapis jawnej wiadomości do lokalnej bazy (STATUS: PENDING)
                String messageId = UUID.randomUUID().toString();
                ChatMessage chatMessage = new ChatMessage(messageId);
                chatMessage.conversationId = conversationId;
                chatMessage.timestamp = System.currentTimeMillis();
                chatMessage.plaintext = rawText;
                chatMessage.isOutgoing = true;
                chatMessage.status = 0; // PENDING
                db.chatDao().insertMessage(chatMessage);

                // 2. Przygotowanie struktury JSON z metadanymi wewnątrz szyfrowanego tunelu
                JsonObject internalPayload = new JsonObject();
                internalPayload.addProperty("id", messageId);
                internalPayload.addProperty("t", chatMessage.timestamp);
                internalPayload.addProperty("msg", rawText);
                String internalJson = new Gson().toJson(internalPayload);

                byte[] rawBytes = internalJson.getBytes(StandardCharsets.UTF_8);

                // 3. Kompresja GZIP i Padding (ukrywanie długości wiadomości)
                byte[] compressedBytes = StegoEngine.compressGzip(rawBytes);
                int paddingLength = PADDING_BLOCK_SIZE - (compressedBytes.length % PADDING_BLOCK_SIZE);
                byte[] paddedBytes = new byte[compressedBytes.length + paddingLength];
                System.arraycopy(compressedBytes, 0, paddedBytes, 0, compressedBytes.length);

                // Wypełnianie paddingu losowym szumem z SecureRandom
                SecureRandom random = new SecureRandom();
                byte[] paddingNoise = new byte[paddingLength];
                random.nextBytes(paddingNoise);
                System.arraycopy(paddingNoise, 0, paddedBytes, compressedBytes.length, paddingLength);

                // 4. Kryptografia: Generowanie kluczy i IV
                SecretKey sessionKey = CryptoEngine.generateSessionKey();
                byte[] iv = new byte[CryptoEngine.GCM_IV_LENGTH];
                random.nextBytes(iv);

                // 5. Kryptografia: Szyfrowanie GCM i kapsułkowanie klucza
                byte[] ciphertext = CryptoEngine.encryptAESGCM(paddedBytes, sessionKey, iv);
                byte[] encryptedSessionKey = CryptoEngine.encapsulateSessionKey(sessionKey, recipientPublicKey);
                byte[] signature = CryptoEngine.signData(ciphertext);

                // 6. Budowanie binarnej paczki
                StegoPayload stegoPayload = new StegoPayload(iv, encryptedSessionKey, signature, ciphertext);
                byte[] finalBinaryPayload = stegoPayload.toBytes();

                // 7. Sieć: Pobieranie mema przykrywkowego (.jpg z reguły)
                Log.d(TAG, "Pobieranie mema maskującego...");
                byte[] memeBytes = NetworkOrchestrator.fetchRandomMemeBytes().join();
                Bitmap rawMemeBitmap = BitmapFactory.decodeByteArray(memeBytes, 0, memeBytes.length);

                // 8. Steganografia: Osadzanie danych w LSB pikseli
                Log.d(TAG, "Osadzanie LSB...");
                Bitmap stegoBitmap = StegoEngine.embedData(rawMemeBitmap, finalBinaryPayload, channelPrngSeed);

                // 9. Konwersja z powrotem do PNG (wymagane bezstratne!)
                ByteArrayOutputStream pngOut = new ByteArrayOutputStream();
                stegoBitmap.compress(Bitmap.CompressFormat.PNG, 100, pngOut);
                byte[] stegoPngBytes = pngOut.toByteArray();

                // 10. Sieć: Wysłanie do kanału Matrix
                Log.d(TAG, "Wysyłanie do Matrixa...");
                boolean isSent = NetworkOrchestrator.sendStegoImageToMatrix(stegoPngBytes, matrixToken, matrixRoomId).join();

                // 11. Aktualizacja statusu w bazie na SENT
                if (isSent) {
                    db.chatDao().updateMessageStatus(messageId, 1); // 1 = SENT
                    Log.d(TAG, "Wiadomość z powodzeniem wstrzyknięta i wysłana.");
                }

                return isSent;

            } catch (Exception e) {
                Log.e(TAG, "Błąd podczas przetwarzania wiadomości", e);
                return false;
            }
        });
    }
}