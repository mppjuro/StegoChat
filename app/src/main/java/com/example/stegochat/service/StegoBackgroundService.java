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
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.example.stegochat.StegoApplication;
import com.example.stegochat.crypto.CryptoEngine;
import com.example.stegochat.db.AppDatabase;
import com.example.stegochat.db.ChatMessage;
import com.example.stegochat.db.Contact;
import com.example.stegochat.domain.MessageProcessor;

import java.security.PublicKey;
import java.util.concurrent.TimeUnit;

public class StegoBackgroundService extends Service {

    private static final String TAG = "StegoService";
    private static final String CHANNEL_ID = "StegoServiceChannel";

    // Nowa akcja pozwalająca UI wymusić natychmiastowe wysłanie
    public static final String ACTION_SEND_PENDING = "com.example.stegochat.SEND_PENDING";

    private SyncEngine syncEngine;
    private AppDatabase db;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Inicjalizacja usługi w tle...");

        createNotificationChannel();
        db = ((StegoApplication) getApplication()).getDatabase();

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Synchronizacja w tle")
                .setContentText("Aplikacja nasłuchuje nowych wiadomości.")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build();

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

        // Zlecamy cykliczne wysyłanie szumu WorkManagerowi
        setupWorkManager();
    }

    private void setupWorkManager() {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        PeriodicWorkRequest coverTrafficWork = new PeriodicWorkRequest.Builder(CoverTrafficWorker.class, 30, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "CoverTrafficWork",
                ExistingPeriodicWorkPolicy.KEEP,
                coverTrafficWork
        );
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Jeśli serwis otrzyma Intent z flagą ACTION_SEND_PENDING, pcha wiadomość od razu
        if (intent != null && ACTION_SEND_PENDING.equals(intent.getAction())) {
            triggerImmediateSend();
        }
        return START_STICKY;
    }

    private void triggerImmediateSend() {
        Log.d(TAG, "Wyzwalanie natychmiastowego wysyłania (użytkownik kliknął wyślij)...");
        new Thread(() -> {
            try {
                ChatMessage pending = db.chatDao().getNextPendingMessage();
                if (pending != null) {

                    PublicKey recipientPublicKey = null;
                    // Pobranie identyfikatora konwersacji
                    String conversationKey = pending.conversationId;

                    if (conversationKey != null && !conversationKey.equals("self_conversation")) {
                        // 1. Rozmowa z inną osobą
                        com.example.stegochat.db.Contact recipientContact = db.contactDao().getContactByConversationId(conversationKey);
                        if (recipientContact != null) {
                            // Konwersja z Base64 na PublicKey
                            recipientPublicKey = com.example.stegochat.crypto.CryptoEngine.decodePublicKey(recipientContact.pubKeyBase64);
                        } else {
                            Log.e(TAG, "Przerwano wysyłanie: Nie znaleziono kontaktu w bazie dla klucza " + conversationKey);
                            return; // Bezwzględnie przerywamy, brak klucza spowoduje crash szyfrowania
                        }
                    } else {
                        // 2. Logika dla "self_conversation" (rozmowa ze sobą)
                        // Pobieramy nasz własny sprzętowy klucz publiczny z KeyStore
                        recipientPublicKey = com.example.stegochat.crypto.CryptoEngine.getMyPublicKey();

                        if (recipientPublicKey == null) {
                            Log.e(TAG, "Przerwano wysyłanie: Nie udało się pobrać własnego klucza z KeyStore.");
                            return; // Bezwzględnie przerywamy
                        }
                    }

                    // Wypchnięcie wiadomości z bezpiecznym kluczem (nigdy null)
                    MessageProcessor.processAndSendMessage(
                            pending.plaintext,
                            conversationKey != null ? conversationKey : "default_conversation",
                            recipientPublicKey,
                            "!PhcUBJdMvnzrXbIrFe:matrix.org",
                            "mct_9EdOHRAQ9PAEucY8YmXUtMhDDoDQKN_nDZD13", // Czysty token bez polskich znaków
                            12345L,
                            pending.isHandshake,
                            db
                    ).join();

                    Log.d(TAG, "Prawdziwa wiadomość została pomyślnie przepchnięta w eter!");
                }
            } catch (Exception e) {
                Log.e(TAG, "Błąd podczas natychmiastowego wysyłania", e);
            }
        }).start();
    }

    @Override
    public void onDestroy() {
        if (syncEngine != null) syncEngine.stop();
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
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }
}