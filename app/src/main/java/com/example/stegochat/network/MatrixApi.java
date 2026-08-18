package com.example.stegochat.network;

import com.google.gson.JsonObject;

import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;
import retrofit2.http.PUT;

public interface MatrixApi {

    @POST("_matrix/media/v3/upload")
    Call<UploadResponse> uploadMedia(
            @Header("Authorization") String bearerToken,
            @Header("Content-Type") String contentType,
            @Query("filename") String filename,
            @Body RequestBody fileData
    );

    @PUT("_matrix/client/v3/rooms/{roomId}/send/m.room.message/{txnId}")
    Call<Void> sendMessage(
            @Header("Authorization") String bearerToken,
            @Path("roomId") String roomId,
            @Path("txnId") String txnId,
            @Body MessageBody messageBody
    );

    @GET("_matrix/client/v3/sync")
    Call<JsonObject> sync(
            @Header("Authorization") String bearerToken,
            @Query("since") String sinceToken,
            @Query("timeout") int timeoutMs
    );

    class UploadResponse {
        public String content_uri;
    }

    class MessageBody {
        public String msgtype;
        public String body;
        public String url;
        public FileInfo info;

        public MessageBody(String msgtype, String body, String url, String mimetype, long size) {
            this.msgtype = msgtype;
            this.body = body;
            this.url = url;
            this.info = new FileInfo(mimetype, size);
        }
    }

    class FileInfo {
        public String mimetype;
        public long size;

        public FileInfo(String mimetype, long size) {
            this.mimetype = mimetype;
            this.size = size;
        }
    }
}