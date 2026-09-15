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
    public static final long MAX_PIXEL_COUNT = 1_500_000L; // 1.5 MPix
    public static final int MAX_LSB_CAPACITY_BYTES = (int) ((MAX_PIXEL_COUNT * 3) / 8); // ~562.5 kB

    /**
     * Wstępne przeliczanie wielkości payloadu uwzględniające UTF-8, nagłówki, AES-GCM i podpis cyfrowy.
     */
    public static int calculatePayloadSize(String text) {
        if (text == null) return 0;

        // Zabezpieczenie przed zamrożeniem wątku UI dla absurdalnych wklejek
        if (text.length() > 500_000) {
            return text.length() * 2;
        }

        try {
            JsonObject internalPayload = new JsonObject();
            String mockPubKey = new String(new char[800]).replace('\0', 'A');
            internalPayload.addProperty("senderPubKey", mockPubKey);
            internalPayload.addProperty("type", "chat");
            internalPayload.addProperty("id", UUID.randomUUID().toString());
            internalPayload.addProperty("t", System.currentTimeMillis());
            internalPayload.addProperty("msg", text);

            // GSON z disableHtmlEscaping i konwersja na bajty z uwzględnieniem UTF-8
            com.google.gson.Gson customGson = new com.google.gson.GsonBuilder().disableHtmlEscaping().create();
            String internalJson = customGson.toJson(internalPayload);
            byte[] rawBytes = internalJson.getBytes(StandardCharsets.UTF_8);

            byte[] compressedBytes = StegoEngine.compressGzip(rawBytes);
            int paddingLength = PADDING_BLOCK_SIZE - (compressedBytes.length % PADDING_BLOCK_SIZE);
            int paddedLength = compressedBytes.length + paddingLength;

            int ciphertextLength = paddedLength + 16;
            int overhead = 1041;

            return ciphertextLength + overhead;
        } catch (Exception e) {
            return text.getBytes(StandardCharsets.UTF_8).length + 2048;
        }
    }

    /**
     * Oblicza dokładną pojemność steganograficzną konkretnego mema w bajtach bez jego pełnego dekodowania.
     */
    public static int calculateMemeCapacityBytes(byte[] memeBytes) {
        if (memeBytes == null) return MAX_LSB_CAPACITY_BYTES;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true; // Czyta tylko nagłówek pliku bez alokacji pikseli w RAM
        BitmapFactory.decodeByteArray(memeBytes, 0, memeBytes.length, options);

        int width = options.outWidth;
        int height = options.outHeight;

        if (width <= 0 || height <= 0) {
            return MAX_LSB_CAPACITY_BYTES;
        }

        // Symulacja pętli skalowania na samych wymiarach
        while ((long) width * height > MAX_PIXEL_COUNT) {
            width = Math.max(1, width / 2);
            height = Math.max(1, height / 2);
            if (width == 1 && height == 1) break;
        }

        return (width * height * 3) / 8;
    }

    public static CompletableFuture<Boolean> processAndSendMessage(
            String rawText,
            String existingMessageId,
            String conversationId,
            PublicKey recipientPublicKey,
            String matrixRoomId,
            String matrixToken,
            long channelPrngSeed,
            boolean isHandshake,
            AppDatabase db,
            byte[] preFetchedMemeBytes) {

        return CompletableFuture.supplyAsync(() -> {
            try {
                String messageId = existingMessageId != null ? existingMessageId : UUID.randomUUID().toString();
                long timestamp = System.currentTimeMillis();
                String myPubKeyBase64 = CryptoEngine.encodePublicKey(CryptoEngine.getMyPublicKey());

                boolean isCoverTraffic = "COVER_TRAFFIC_JUNK_DATA".equals(rawText);

                // 1. Zapis do bazy
                if (!isHandshake && !isCoverTraffic && existingMessageId == null) {
                    ChatMessage chatMessage = new ChatMessage(messageId);
                    chatMessage.conversationId = conversationId;
                    chatMessage.timestamp = timestamp;
                    chatMessage.plaintext = rawText;
                    chatMessage.isOutgoing = true;
                    chatMessage.status = 0; // PENDING
                    db.chatDao().insertMessage(chatMessage);
                }

                JsonObject internalPayload = new JsonObject();
                internalPayload.addProperty("senderPubKey", myPubKeyBase64);

                if (isHandshake) {
                    internalPayload.addProperty("type", "handshake");
                    internalPayload.addProperty("convId", conversationId);
                } else {
                    internalPayload.addProperty("type", "chat");
                    internalPayload.addProperty("id", messageId);
                    internalPayload.addProperty("t", timestamp);
                    internalPayload.addProperty("msg", rawText);
                }

                com.google.gson.Gson customGson = new com.google.gson.GsonBuilder().disableHtmlEscaping().create();
                String internalJson = customGson.toJson(internalPayload);
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

                // KROK 6: Pobranie przykrywki
                byte[] memeBytes = preFetchedMemeBytes != null ? preFetchedMemeBytes : NetworkOrchestrator.fetchRandomMemeBytes().join();
                Bitmap rawMemeBitmap = BitmapFactory.decodeByteArray(memeBytes, 0, memeBytes.length);

                // Skalowanie mema do maksymalnie 1.5 MPix
                rawMemeBitmap = scaleBitmapIfNeeded(rawMemeBitmap);

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    if (rawMemeBitmap.getColorSpace() != null &&
                            !rawMemeBitmap.getColorSpace().equals(android.graphics.ColorSpace.get(android.graphics.ColorSpace.Named.SRGB))) {
                        Bitmap srgbBitmap = rawMemeBitmap.copy(Bitmap.Config.ARGB_8888, true);
                        srgbBitmap.setColorSpace(android.graphics.ColorSpace.get(android.graphics.ColorSpace.Named.SRGB));
                        rawMemeBitmap.recycle();
                        rawMemeBitmap = srgbBitmap;
                    }
                }

                Bitmap stegoBitmap = StegoEngine.embedData(rawMemeBitmap, finalBinaryPayload, channelPrngSeed);
                // KROK 7: Konwersja na PNG
                ByteArrayOutputStream pngOut = new ByteArrayOutputStream();
                stegoBitmap.compress(Bitmap.CompressFormat.PNG, 100, pngOut);
                byte[] stegoPngBytes = pngOut.toByteArray();

                // 8. Opublikowanie na Matrixie
                boolean isSent = NetworkOrchestrator.sendStegoImageToMatrix(stegoPngBytes, matrixToken, matrixRoomId).join();

                // 9. Aktualizacja statusu
                if (isSent && !isHandshake && !isCoverTraffic) {
                    db.chatDao().updateMessageStatus(messageId, 1);
                }

                return isSent;

            } catch (Exception e) {
                Log.e(TAG, "Błąd podczas przetwarzania wiadomości", e);
                return false;
            }
        });
    }

    /**
     * Skaluje bitmapę o 50% w dół w pętli, dopóki jej powierzchnia przekracza 1.5 MPix.
     * Natychmiast zwalnia pamięć natywną poprzednich instancji (zapobiega OOM).
     */
    public static Bitmap scaleBitmapIfNeeded(Bitmap source) {
        if (source == null) return null;

        Bitmap current = source;
        while ((long) current.getWidth() * current.getHeight() > MAX_PIXEL_COUNT) {
            int nextWidth = Math.max(1, current.getWidth() / 2);
            int nextHeight = Math.max(1, current.getHeight() / 2);

            // Skalowanie z filtrowaniem dwuliniowym (wyższa jakość nośnika steganograficznego)
            Bitmap next = Bitmap.createScaledBitmap(current, nextWidth, nextHeight, true);

            if (current != next) {
                current.recycle();
            }
            current = next;

            if (nextWidth == 1 && nextHeight == 1) {
                break;
            }
        }
        return current;
    }
}