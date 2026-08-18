package com.example.stegochat.network;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.ByteArrayOutputStream;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import retrofit2.Response;

public class NetworkOrchestrator {

    /**
     * Pobiera losowego mema, a następnie pobiera jego bajty.
     * Zwraca tablicę bajtów czystego obrazka (najczęściej .jpg z Reddita).
     */
    public static CompletableFuture<byte[]> fetchRandomMemeBytes() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Response<MemeApi.MemeResponse> response = ApiClient.getMemeApi().getRandomMeme().execute();
                if (response.isSuccessful() && response.body() != null && response.body().url != null) {

                    String imageUrl = response.body().url;

                    // Bezpośrednie pobranie bajtów obrazka
                    URL url = new URL(imageUrl);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setDoInput(true);
                    connection.connect();

                    InputStream input = connection.getInputStream();
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    byte[] buffer = new byte[4096];
                    int n;
                    while ((n = input.read(buffer)) != -1) {
                        output.write(buffer, 0, n);
                    }
                    input.close();
                    return output.toByteArray();
                } else {
                    throw new RuntimeException("Nie udało się pobrać metadanych mema.");
                }
            } catch (Exception e) {
                throw new RuntimeException("Błąd pobierania mema: " + e.getMessage());
            }
        });
    }

    /**
     * Wysyła steganograficzny obrazek (PNG) na serwer Matrix jako plik m.file (bez kompresji).
     */
    public static CompletableFuture<Boolean> sendStegoImageToMatrix(
            byte[] pngBytes, String matrixToken, String roomId) {

        return CompletableFuture.supplyAsync(() -> {
            try {
                // KROK 1: Upload pliku
                RequestBody requestBody = RequestBody.create(MediaType.parse("image/png"), pngBytes);
                // Losowa nazwa ułatwia zamaskowanie pliku jako zwykły zrzut ekranu/mema
                String filename = "meme_" + UUID.randomUUID().toString().substring(0, 8) + ".png";

                String bearer = "Bearer " + matrixToken;

                Response<MatrixApi.UploadResponse> uploadResponse =
                        ApiClient.getMatrixApi().uploadMedia(bearer, filename, requestBody).execute();

                if (!uploadResponse.isSuccessful() || uploadResponse.body() == null) {
                    throw new RuntimeException("Błąd wgrywania pliku na Matrix.");
                }

                String contentUri = uploadResponse.body().content_uri;

                // KROK 2: Wysłanie wiadomości na kanał
                String txnId = UUID.randomUUID().toString();
                MatrixApi.MessageBody msgBody = new MatrixApi.MessageBody(
                        "m.file",
                        filename,
                        contentUri,
                        "image/png",
                        pngBytes.length
                );

                Response<Void> sendResponse =
                        ApiClient.getMatrixApi().sendMessage(bearer, roomId, txnId, msgBody).execute();

                return sendResponse.isSuccessful();

            } catch (Exception e) {
                throw new RuntimeException("Błąd wysyłania do Matrix: " + e.getMessage());
            }
        });
    }
}
