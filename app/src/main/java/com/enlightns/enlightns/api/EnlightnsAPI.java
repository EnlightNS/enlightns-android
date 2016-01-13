package com.enlightns.enlightns.api;


import java.util.List;

import retrofit.http.Body;
import retrofit.http.GET;
import retrofit.http.Header;
import retrofit.http.Headers;
import retrofit.http.POST;
import retrofit.http.PUT;
import retrofit.http.Path;
import retrofit.http.Query;

public interface EnlightnsAPI {

    @Headers("Content-Type: application/json")
    @POST("/api-token-auth/")
    Token getAuthToken(@Body AuthUser authUser);

    @GET("/user/record/")
    List<ApiRecord> getUserRecords(@Header("Authorization") String token);

    @GET("/user/record/")
    List<ApiRecord> getUserRecords(@Header("Authorization") String token,
                                   @Query("type") String filter);

    @GET("/tools/whatismyip/")
    IP getWanIp();

    @PUT("/user/record/{id}/")
    ApiRecord updateRecordContent(@Header("Authorization") String token, @Path("id") String id,
                                  @Body Content content);

    class AuthUser {
        final String email;
        final String password;

        public AuthUser(String email, String password) {
            this.email = email;
            this.password = password;
        }
    }

    class Token {
        public String token;
    }

    class IP {
        public String ip;
    }

    class Content {
        public final String content;

        public Content(String content) {
            this.content = content;
        }
    }

    class ApiRecord {
        public long id;
        public String name;
        public String type;
        public String content;
        public int ttl;
        public boolean active;
    }

}
