package com.enlightns.enlightns.api;


import android.util.Log;

import com.enlightns.enlightns.BuildConfig;

import retrofit.ErrorHandler;
import retrofit.RestAdapter;
import retrofit.RetrofitError;
import retrofit.client.Response;

public class EnlightnsAPIHelper {

    private static final String TAG = EnlightnsAPIHelper.class.getName();

    public EnlightnsAPIHelper() {
    }

    public EnlightnsAPI getEnlightnsAPI() {
        RestAdapter restAdapter = new RestAdapter.Builder()
                .setEndpoint(BuildConfig.API_ENDPOINT)
                .setErrorHandler(new EnlightnsErrorHandler())
                .build();

        return restAdapter.create(EnlightnsAPI.class);
    }

    private class EnlightnsErrorHandler implements ErrorHandler {

        @Override
        public Throwable handleError(RetrofitError cause) {
            Response r = cause.getResponse();
            if (r != null) {
                switch (r.getStatus()) {
                    case 500:
                        Log.d(TAG, "500 error during request to " + r.getUrl());
                        return new ServerErrorException(cause.getMessage());
                    case 403:
                        Log.d(TAG, "403 error during request to " + r.getUrl());
                        return new ForbiddenException(cause.getMessage());
                    case 404:
                        Log.d(TAG, "404 error during request to " + r.getUrl());
                        return new NotFoundException(cause.getMessage());
                    case 400:
                        Log.d(TAG, "400 error during request to " + r.getUrl());
                        return new BadRequestException(cause.getMessage());
                }
            }

            return cause;
        }
    }

    public class ForbiddenException extends RuntimeException {
        public ForbiddenException(String message) {
            super(message);
        }
    }

    public class ServerErrorException extends RuntimeException {
        public ServerErrorException(String message) {
            super(message);
        }
    }

    public class NotFoundException extends RuntimeException {
        public NotFoundException(String message) {
            super(message);
        }
    }

    public class BadRequestException extends RuntimeException {
        public BadRequestException(String message) {
            super(message);
        }
    }
}
