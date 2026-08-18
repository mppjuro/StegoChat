package com.example.stegochat.network;

import retrofit2.Call;
import retrofit2.http.GET;

public interface MemeApi {

    // Zwraca losowego mema z Reddita
    @GET("gimme")
    Call<MemeResponse> getRandomMeme();

    // Model odpowiedzi JSON
    class MemeResponse {
        public String postLink;
        public String subreddit;
        public String title;
        public String url;     // Bezpośredni link do obrazka (np. .jpg / .png)
        public boolean nsfw;
        public boolean spoiler;
    }
}