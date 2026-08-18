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
    private static final int PADDING_BLOCK_SIZE = 1024;

    public static CompletableFuture<Boolean> processAndSendMessage(
            String rawText,
            String conversationId,
            PublicKey recipientPublicKey,
            String matrixRoomId,
            String matrixToken,
            long channelPrngSeed,
            boolean isHandshake, // NOWA FLAGA
            AppDatabase db) {

        return CompletableFuture.supplyAsync(() -> {
            try {
                String messageId = UUID.randomUUID().toString();
                long timestamp = System.currentTimeMillis();

                // 1. Zapis do bazy TYLKO jeśli to nie jest Handshake
                if (!isHandshake) {
                    ChatMessage chatMessage = new ChatMessage(messageId);
                    chatMessage.conversationId = conversationId;
                    chatMessage.timestamp = timestamp;
                    chatMessage.plaintext = rawText;
                    chatMessage.isOutgoing = true;
                    chatMessage.status = 0; // PENDING
                    db.chatDao().insertMessage(chatMessage);
                }

                // 2. Przygotowanie struktury JSON zależnej od typu wiadomości
                JsonObject internalPayload = new JsonObject();
                if (isHandshake) {
                    internalPayload.addProperty("type", "handshake");
                    // Dla handshake'a rawText zawiera nasz klucz publiczny w Base64
                    internalPayload.addProperty("senderPubKey", rawText);
                } else {
                    internalPayload.addProperty("type", "chat");
                    internalPayload.addProperty("id", messageId);
                    internalPayload.addProperty("t", timestamp);
                    internalPayload.addProperty("msg", rawText);
                }

                String internalJson = new Gson().toJson(internalPayload);
                byte[] rawBytes = internalJson.getBytes(StandardCharsets.UTF_8);

                // 3. Kompresja GZIP i Padding
                byte[] compressedBytes = StegoEngine.compressGzip(rawBytes);
                int paddingLength = PADDING_BLOCK_SIZE - (compressedBytes.length % PADDING_BLOCK_SIZE);
                byte[] paddedBytes = new byte[compressedBytes.length + paddingLength];
                System.arraycopy(compressedBytes, 0, paddedBytes, 0, compressedBytes.length);

                SecureRandom random = new SecureRandom();
                byte[] paddingNoise = new byte[paddingLength];
                random.nextBytes(paddingNoise);
                System.arraycopy(paddingNoise, 0, paddedBytes, compressedBytes.length, paddingLength);

                // 4. Kryptografia: AES-GCM
                SecretKey sessionKey = CryptoEngine.generateSessionKey();
                byte[] iv = new byte[CryptoEngine.GCM_IV_LENGTH];
                random.nextBytes(iv);

                byte[] ciphertext = CryptoEngine.encryptAESGCM(paddedBytes, sessionKey, iv);
                byte[] encryptedSessionKey = CryptoEngine.encapsulateSessionKey(sessionKey, recipientPublicKey);
                byte[] signature = CryptoEngine.signData(ciphertext);

                // 5. Budowanie paczki steganograficznej
                StegoPayload stegoPayload = new StegoPayload(iv, encryptedSessionKey, signature, ciphertext);
                byte[] finalBinaryPayload = stegoPayload.toBytes();

                // 6. Pobranie przykrywki i wstrzyknięcie LSB
                byte[] memeBytes = NetworkOrchestrator.fetchRandomMemeBytes().join();
                Bitmap rawMemeBitmap = BitmapFactory.decodeByteArray(memeBytes, 0, memeBytes.length);
                Bitmap stegoBitmap = StegoEngine.embedData(rawMemeBitmap, finalBinaryPayload, channelPrngSeed);

                // 7. Konwersja na PNG
                ByteArrayOutputStream pngOut = new ByteArrayOutputStream();
                stegoBitmap.compress(Bitmap.CompressFormat.PNG, 100, pngOut);
                byte[] stegoPngBytes = pngOut.toByteArray();

                // 8. Opublikowanie na Matrixie
                boolean isSent = NetworkOrchestrator.sendStegoImageToMatrix(stegoPngBytes, matrixToken, matrixRoomId).join();

                // 9. Aktualizacja statusu
                if (isSent && !isHandshake) {
                    db.chatDao().updateMessageStatus(messageId, 1);
                }

                return isSent;

            } catch (Exception e) {
                Log.e(TAG, "Błąd podczas przetwarzania wiadomości", e);
                return false;
            }
        });
    }
}