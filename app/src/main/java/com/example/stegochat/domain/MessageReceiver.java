package com.example.stegochat.domain;

import android.graphics.Bitmap;
import android.util.Log;

import com.example.stegochat.crypto.CryptoEngine;
import com.example.stegochat.crypto.StegoEngine;
import com.example.stegochat.db.AppDatabase;
import com.example.stegochat.db.ChatMessage;
import com.example.stegochat.db.Contact;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.Signature;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public class MessageReceiver {

    private static final String TAG = "MessageReceiver";

    public static CompletableFuture<Boolean> processIncomingImage(
            Bitmap stegoImage,
            long channelPrngSeed,
            AppDatabase db) {

        return CompletableFuture.supplyAsync(() -> {
            try {
                // 1. Ekstrakcja danych ze steganografii
                byte[] extractedBinary = StegoEngine.extractData(stegoImage, channelPrngSeed);
                StegoPayload payload = StegoPayload.fromBytes(extractedBinary);

                // 2. Deszyfrowanie sesji AES naszym prywatnym kluczem z Keystore
                byte[] sessionKeyBytes = CryptoEngine.decapsulateSessionKey(payload.encryptedSessionKey);
                SecretKey sessionKey = new SecretKeySpec(sessionKeyBytes, 0, sessionKeyBytes.length, "AES");

                // 3. Odszyfrowanie GCM i dekompresja GZIP
                byte[] paddedGzipBytes = CryptoEngine.decryptAESGCM(payload.ciphertext, sessionKey, payload.iv);
                byte[] jsonBytes = StegoEngine.decompressGzip(paddedGzipBytes);
                String jsonString = new String(jsonBytes, StandardCharsets.UTF_8);

                // 4. Parsowanie payloadu JSON
                JsonObject internalPayload = new Gson().fromJson(jsonString, JsonObject.class);
                if (!internalPayload.has("senderPubKey")) {
                    throw new Exception("Brak klucza publicznego nadawcy w pakiecie.");
                }

                String senderPubKeyBase64 = internalPayload.get("senderPubKey").getAsString();
                String msgType = internalPayload.has("type") ? internalPayload.get("type").getAsString() : "chat";

                // 5. Weryfikacja podpisu cyfrowego nadawcy
                PublicKey senderPublicKey = CryptoEngine.decodePublicKey(senderPubKeyBase64);
                Signature sig = Signature.getInstance("SHA256withRSA/PSS");
                sig.initVerify(senderPublicKey);
                sig.update(payload.ciphertext);
                if (!sig.verify(payload.signature)) {
                    throw new Exception("Błąd weryfikacji podpisu! Wiadomość sfałszowana.");
                }

                String conversationId;

                // 6. Obsługa Handshake (automatyczne dodanie kontaktu u osoby pokazującej QR)
                if ("handshake".equals(msgType)) {
                    conversationId = internalPayload.has("convId") ? internalPayload.get("convId").getAsString() : UUID.randomUUID().toString();

                    Contact existing = db.contactDao().getContactByKey(senderPubKeyBase64);
                    if (existing == null) {
                        Contact newContact = new Contact(senderPubKeyBase64);
                        newContact.name = "Znajomy (z QR)";
                        newContact.conversationId = conversationId;
                        db.contactDao().insertContact(newContact);
                        Log.d(TAG, "Zapisano nowy kontakt z Handshake! Konwersacja: " + conversationId);
                    }
                    return true;
                }

                // 7. Obsługa wiadomości czatu (ustalanie konwersacji na podstawie nadawcy)
                Contact contact = db.contactDao().getContactByKey(senderPubKeyBase64);
                if (contact == null) {
                    Log.w(TAG, "Otrzymano wiadomość od nieznanego kontaktu (brak handshake'u).");
                    return false;
                }
                conversationId = contact.conversationId;

                // 8. Zapis wiadomości do bazy danych we właściwej konwersacji
                String msgId = internalPayload.get("id").getAsString();
                long timestamp = internalPayload.get("t").getAsLong();
                String text = internalPayload.get("msg").getAsString();

                ChatMessage chatMessage = new ChatMessage(msgId);
                chatMessage.conversationId = conversationId;
                chatMessage.timestamp = timestamp;
                chatMessage.plaintext = text;
                chatMessage.isOutgoing = false;
                chatMessage.status = 2; // 2 = DELIVERED

                db.chatDao().insertMessage(chatMessage);
                Log.d(TAG, "Zapisano wiadomość w konwersacji: " + conversationId);

                return true;

            } catch (Exception e) {
                Log.v(TAG, "Ignorowanie obrazka: " + e.getMessage());
                return false;
            }
        });
    }
}