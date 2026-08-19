package com.example.stegochat.service;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.stegochat.StegoApplication;
import com.example.stegochat.db.AppDatabase;
import com.example.stegochat.domain.MessageProcessor;

public class CoverTrafficWorker extends Worker {

    public CoverTrafficWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d("CoverTrafficWorker", "Wyzwalanie sztucznego ruchu (Cover Traffic) przez WorkManager...");
        try {
            AppDatabase db = ((StegoApplication) getApplicationContext()).getDatabase();
            String textToHide = "COVER_TRAFFIC_JUNK_DATA";

            MessageProcessor.processAndSendMessage(
                    textToHide,
                    "default_conversation",
                    null,
                    null,
                    "!PhcUBJdMvnzrXbIrFe:matrix.org",
                    "mct_9EdOHRAQ9PAEucY8YmXUtMhDDoDQKN_nDZD13",
                    12345L,
                    false,
                    db
            ).join();

            Log.d("CoverTrafficWorker", "Sztuczny szum wysłany poprawnie.");
            return Result.success();
        } catch (Exception e) {
            Log.e("CoverTrafficWorker", "Błąd podczas wysyłania Cover Traffic", e);
            return Result.retry();
        }
    }
}