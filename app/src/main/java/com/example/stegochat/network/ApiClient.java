package com.example.stegochat.network;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {

    private static final String MEME_BASE_URL = "https://meme-api.com/";
    // Główny publiczny serwer Matrixa. Możesz go później zmienić na dowolny inny serwer Matrix.
    private static final String MATRIX_BASE_URL = "https://matrix-client.matrix.org/";

    private static MemeApi memeApi;
    private static MatrixApi matrixApi;
    private static OkHttpClient okHttpClient;

    private static OkHttpClient getOkHttpClient() {
        if (okHttpClient == null) {
            okHttpClient = new OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    // Tutaj w przyszłości można dodać interceptory np. do logowania ruchu lub Certificate Pinning
                    .build();
        }
        return okHttpClient;
    }

    public static MemeApi getMemeApi() {
        if (memeApi == null) {
            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl(MEME_BASE_URL)
                    .client(getOkHttpClient())
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
            memeApi = retrofit.create(MemeApi.class);
        }
        return memeApi;
    }

    public static MatrixApi getMatrixApi() {
        if (matrixApi == null) {
            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl(MATRIX_BASE_URL)
                    .client(getOkHttpClient())
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
            matrixApi = retrofit.create(MatrixApi.class);
        }
        return matrixApi;
    }
}