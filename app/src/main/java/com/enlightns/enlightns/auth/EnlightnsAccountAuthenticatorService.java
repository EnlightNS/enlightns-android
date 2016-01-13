package com.enlightns.enlightns.auth;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

public class EnlightnsAccountAuthenticatorService extends Service {

    private static final String TAG = EnlightnsAccountAuthenticatorService.class.getName();

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "Service onBind");
        EnlightnsAccountAuthenticator authenticator = new EnlightnsAccountAuthenticator(this);
        return authenticator.getIBinder();
    }
}
