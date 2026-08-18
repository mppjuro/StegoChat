package com.example.stegochat.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.content.pm.ServiceInfo;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.stegochat.StegoApplication;
import com.example.stegochat.crypto.CryptoEngine;
import com.example.stegochat.db.AppDatabase;
import com.example.stegochat.db.ChatMessage;
import com.example.stegochat.domain.MessageProcessor;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class StegoBackgroundService extends Service {

    private static final String TAG = "StegoService";
    private static final String CHANNEL_ID = "StegoServiceChannel";

    private SyncEngine syncEngine;
    private ScheduledExecutorService scheduler;
    private AppDatabase db;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Inicjalizacja usługi w tle...");

        createNotificationChannel();
        db = ((StegoApplication) getApplication()).getDatabase();

        // Powiadomienie wymagane dla Foreground Service
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Synchronizacja w tle")
                .setContentText("Aplikacja nasłuchuje nowych wiadomości.")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build();

        // POPRAWKA: Jawne wskazanie typu usługi (Wymóg Android 14+ / API 34+)
        // Ponieważ nasz minSdk to 29, możemy bezpiecznie użyć tej flagi bez ostrzeżeń IDE.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(1, notification);
        }

        startServices();
    }

    private void startServices() {
        String matrixToken = "mct_9EdOHRAQ9PAEucY8YmXUtMhDDoDQKN_nDZD13";
        String roomId = "!PhcUBJdMvnzrXbIrFe:matrix.org";
        long channelSeed = 12345L;

        syncEngine = new SyncEngine(matrixToken, roomId, db, null, channelSeed);
        syncEngine.startSyncLoop();

        scheduler = Executors.newSingleThreadScheduledExecutor();
        // POPRAWKA: Użycie scheduleWithFixedDelay zamiast scheduleAtFixedRate
        // Zapobiega natychmiastowemu wykonaniu setek zakolejkowanych zadań po wybudzeniu aplikacji
        scheduler.scheduleWithFixedDelay(this::generateCoverTraffic, 5, 30, TimeUnit.MINUTES);
    }

    private void generateCoverTraffic() {
        Log.d(TAG, "Wyzwalanie sztucznego ruchu (Cover Traffic)...");

        new Thread(() -> {
            try {
                // Sprawdzamy czy mamy jakąś prawdziwą wiadomość oczekującą w kolejce
                ChatMessage pending = db.chatDao().getNextPendingMessage();

                String textToHide = (pending != null) ? pending.plaintext : "COVER_TRAFFIC_JUNK_DATA";

                // Wypchnięcie wiadomości (prawdziwej lub sztucznej) w eter
                // Parametry: matrixRoomId, token itd. powinny być tu załadowane z pamięci.
                MessageProcessor.processAndSendMessage(
                        textToHide,
                        "default_conversation",
                        null, // recipientPublicKey
                        "!twójRoomId:matrix.org",
                        "TWÓJ_TOKEN_MATRIX",
                        12345L,
                        db
                ).join();

                if (pending != null) {
                    Log.d(TAG, "Prawdziwa wiadomość została pomyślnie przepchnięta w oknie Cover Traffic!");
                }

            } catch (Exception e) {
                Log.e(TAG, "Błąd podczas generowania Cover Traffic", e);
            }
        }).start();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY; // System automatycznie zrestartuje usługę, jeśli zostanie zabita brakiem pamięci
    }

    @Override
    public void onDestroy() {
        if (syncEngine != null) syncEngine.stop();
        if (scheduler != null) scheduler.shutdownNow();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Usługa nasłuchu StegoChat",
                    NotificationManager.IMPORTANCE_LOW // Low, aby nie wybudzała dźwiękiem co chwilę
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }
}