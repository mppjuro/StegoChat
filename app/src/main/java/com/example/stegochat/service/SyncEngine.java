package com.example.stegochat.service;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import com.example.stegochat.db.AppDatabase;
import com.example.stegochat.domain.MessageReceiver;
import com.example.stegochat.network.ApiClient;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.PublicKey;
import java.util.concurrent.atomic.AtomicBoolean;

import retrofit2.Response;

public class SyncEngine {

    private static final String TAG = "SyncEngine";
    private final String matrixToken;
    private final String roomId;
    private final AppDatabase db;
    private final PublicKey myPartnerPublicKey;
    private final long channelSeed;

    private AtomicBoolean isRunning = new AtomicBoolean(false);
    private String nextBatchToken = ""; // Token paginacji Matrixa

    public SyncEngine(String matrixToken, String roomId, AppDatabase db, PublicKey partnerKey, long seed) {
        this.matrixToken = matrixToken;
        this.roomId = roomId;
        this.db = db;
        this.myPartnerPublicKey = partnerKey;
        this.channelSeed = seed;
    }

    public void startSyncLoop() {
        if (isRunning.get()) return;
        isRunning.set(true);

        new Thread(() -> {
            Log.d(TAG, "Rozpoczynam pętlę synchronizacji Matrix...");
            while (isRunning.get()) {
                try {
                    String bearer = "Bearer " + matrixToken;
                    // Long polling: 10 sekund timeoutu
                    Response<JsonObject> response = ApiClient.getMatrixApi().sync(bearer, nextBatchToken, 10000).execute();

                    if (response.isSuccessful() && response.body() != null) {
                        JsonObject body = response.body();
                        nextBatchToken = body.has("next_batch") ? body.get("next_batch").getAsString() : nextBatchToken;

                        parseAndProcessEvents(body);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Błąd sieci w pętli sync, ponawiam za 5s...", e);
                    sleep(5000);
                }
            }
        }).start();
    }

    public void stop() {
        isRunning.set(false);
    }

    private void parseAndProcessEvents(JsonObject syncResponse) {
        try {
            JsonObject rooms = syncResponse.getAsJsonObject("rooms");
            if (rooms == null) return;

            JsonObject join = rooms.getAsJsonObject("join");
            if (join == null || !join.has(roomId)) return;

            JsonObject roomData = join.getAsJsonObject(roomId);
            JsonArray events = roomData.getAsJsonObject("timeline").getAsJsonArray("events");

            for (JsonElement element : events) {
                JsonObject event = element.getAsJsonObject();
                String type = event.get("type").getAsString();

                if ("m.room.message".equals(type)) {
                    JsonObject content = event.getAsJsonObject("content");
                    String msgtype = content.get("msgtype").getAsString();

                    // Interesują nas tylko pliki (nasze memy PNG wysyłane jako m.file)
                    if ("m.file".equals(msgtype) && content.has("url")) {
                        String mxcUrl = content.get("url").getAsString();
                        downloadAndProcessImage(mxcUrl);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Błąd parsowania zdarzeń: ", e);
        }
    }

    private void downloadAndProcessImage(String mxcUrl) {
        try {
            // Konwersja mxc:// na standardowy URL HTTP z bramki Matrixa
            String httpUrl = mxcUrl.replace("mxc://", "https://matrix-client.matrix.org/_matrix/media/v3/download/");

            URL url = new URL(httpUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            connection.connect();

            InputStream input = connection.getInputStream();
            Bitmap bitmap = BitmapFactory.decodeStream(input);
            input.close();

            if (bitmap != null) {
                // Przekazanie do silnika steganograficznego
                MessageReceiver.processIncomingImage(
                        bitmap, channelSeed, myPartnerPublicKey, "default_conversation", db
                ).join(); // join() czeka na zakończenie asynchronicznej operacji
            }
        } catch (Exception e) {
            Log.e(TAG, "Błąd pobierania obrazka: " + mxcUrl, e);
        }
    }

    private void sleep(long millis) {
        try { Thread.sleep(millis); } catch (InterruptedException ignored) {}
    }
}