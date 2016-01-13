package com.enlightns.enlightns.updater;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.util.Log;

import com.enlightns.enlightns.R;
import com.enlightns.enlightns.RecordListing;
import com.enlightns.enlightns.api.EnlightnsAPI;
import com.enlightns.enlightns.api.EnlightnsAPIHelper;
import com.enlightns.enlightns.auth.EnlightnsAccountAuthenticator;
import com.enlightns.enlightns.db.Record;
import com.enlightns.enlightns.notifications.InvalidCredentialsNotification;
import com.enlightns.enlightns.notifications.UpdateFailedNotification;
import com.enlightns.enlightns.tasks.UpdateDBRecords;
import com.enlightns.enlightns.utils.NetworkUtils;

import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

public class NetworkChangeReceiver extends BroadcastReceiver {

    public static final String MANUAL_UPDATE_ACTION = "com.enlightns.enlightns.action.ManualUpdate";
    private static final String RETRY_FROM_NETWORK_CHANGE_ACTION = "com.enlightns.enlightns.action.RetryNetworkChange";
    private static final String TAG = NetworkChangeReceiver.class.getName();
    private static final String KEY_ALREADY_TRIED_UPDATE = "already_tried_update";

    private SharedPreferences mPrefs;

    public NetworkChangeReceiver() {
    }

    public static void retryRecordUpdateDelayed(Context context, int secondsDelay, String action) {
        Intent retryIntent = new Intent(action);
        retryIntent.putExtra(KEY_ALREADY_TRIED_UPDATE, true);

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context,
                1, retryIntent, PendingIntent.FLAG_CANCEL_CURRENT);
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());
        calendar.add(Calendar.SECOND, secondsDelay);

        Log.d(TAG, "Next update will trigger at " + calendar.getTimeInMillis());

        alarmManager.set(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), pendingIntent);
    }

    @Override
    public void onReceive(Context context, Intent intent) {

        String action = intent.getAction();

        Log.d(TAG, "Received Intent: " + action);

        String[] array = {ConnectivityManager.CONNECTIVITY_ACTION, Intent.ACTION_BOOT_COMPLETED,
                MANUAL_UPDATE_ACTION, RETRY_FROM_NETWORK_CHANGE_ACTION};
        if (!Arrays.asList(array).contains(action)) {
            Log.d(TAG, "Invalid action, exiting");
            return;
        }

        AccountManager accountManager = (AccountManager) context.getSystemService(Context.ACCOUNT_SERVICE);
        Account account = EnlightnsAccountAuthenticator.getAccount(accountManager);

        if (account == null) {
            Log.d(TAG, "No account found, skipping");
            return;
        }

        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(
                Context.CONNECTIVITY_SERVICE);


        ConnectionParams connectionParams = new ConnectionParams(connectivityManager).invoke();
        boolean isConnected = connectionParams.isConnected();
        boolean isWiFi = connectionParams.isWiFi();
        boolean isMobile = connectionParams.isMobile();

        mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean updateOnMobileNetwork = mPrefs.getBoolean(context.getString(R.string.update_on_mobile_key), false);

        boolean wifiConnection = isConnected && isWiFi;
        boolean mobileConnection = isConnected && updateOnMobileNetwork && isMobile;

        if (wifiConnection || mobileConnection) {
            new UpdateRecordsTask(context, isWiFi).execute();
        } else if (!isConnected) {
            Log.d(TAG, "No connection detected");

            if (intent.getBooleanExtra(KEY_ALREADY_TRIED_UPDATE, false)) {
                if (action.equals(MANUAL_UPDATE_ACTION)) {
                    UpdateFailedNotification.notify(context);
                }
                Log.d(TAG, "Update failed twice, skipping");
                return;
            }

            if (action.equals(MANUAL_UPDATE_ACTION))
                retryRecordUpdateDelayed(context, 20, MANUAL_UPDATE_ACTION);
            else
                retryRecordUpdateDelayed(context, 20, RETRY_FROM_NETWORK_CHANGE_ACTION);

        } else {
            Log.d(TAG, "Not updating; Update on mobile network is not enabled");
        }

        RecordListing.broadcastDisplayIPUpdate(context);
    }

    private class UpdateRecordsTask extends AsyncTask<Void, Void, Void> {

        private final Context mContext;
        private final boolean mConnectionIsWifi;

        private UpdateRecordsTask(Context context, boolean connectionIsWifi) {
            this.mContext = context;
            this.mConnectionIsWifi = connectionIsWifi;
        }

        @Override
        protected Void doInBackground(Void... params) {
            UpdateFailedNotification.cancel(mContext);

            List<Record> recordsToUpdate = Record.find(Record.class, "update_active = ?", "1");

            Log.d(TAG, "Got " + recordsToUpdate.size() + " records to update.");

            EnlightnsAPI ensApi = new EnlightnsAPIHelper().getEnlightnsAPI();

            boolean useLanIp = mPrefs.getBoolean(mContext.getString(R.string.use_lan_ip_key), false);
            String ip;

            if (mConnectionIsWifi && useLanIp) {
                ip = NetworkUtils.getLanIp(mContext);
            } else {
                try {
                    Thread.sleep(3000);
                    ip = ensApi.getWanIp().ip;
                } catch (Exception e) {
                    Log.w(TAG, "Error while getting IP", e);
                    return null;
                }
            }

            Log.d(TAG, "IP for update:  " + ip);

            String authToken = EnlightnsAccountAuthenticator.getAccountToken(mContext);

            for (Record record : recordsToUpdate) {
                Log.d(TAG, "Updating Record: " + record.name);

                if (record.content.equals(ip)) {
                    Log.d(TAG, String.format("Content (%s) is the same not calling API", record.content));
                    continue;
                }

                try {
                    ensApi.updateRecordContent(authToken, Long.toString(record.ensId),
                            new EnlightnsAPI.Content(ip));
                } catch (EnlightnsAPIHelper.ForbiddenException fe) {
                    Log.w(TAG, "Credentials are no longer valid, need to log-in again.", fe);
                    InvalidCredentialsNotification.notify(mContext);
                } catch (Exception e) {
                    Log.e(TAG, "Error while updating user records", e);
                }
            }

            try {
                List<EnlightnsAPI.ApiRecord> apiRecords = ensApi.getUserRecords(authToken, "A,AAAA");
                UpdateDBRecords.updateRecords(apiRecords);
            } catch (EnlightnsAPIHelper.ForbiddenException fe) {
                Log.w(TAG, "Credentials are no longer valid, need to log-in again.", fe);
                InvalidCredentialsNotification.notify(mContext);
            } catch (Exception e) {
                Log.e(TAG, "Error while updating local records storage", e);
            }

            Log.d(TAG, "Done updating records");

            RecordListing.broadcastRecordsUpdated(mContext);

            return null;
        }
    }

    private class ConnectionParams {
        private ConnectivityManager connectivityManager;
        private boolean isWiFi;
        private boolean isMobile;
        private boolean isConnected;

        public ConnectionParams(ConnectivityManager connectivityManager) {
            this.connectivityManager = connectivityManager;
        }

        public boolean isWiFi() {
            return isWiFi;
        }

        public boolean isMobile() {
            return isMobile;
        }

        public boolean isConnected() {
            return isConnected;
        }

        public ConnectionParams invoke() {
            NetworkInfo netInfo = connectivityManager.getActiveNetworkInfo();
            int networkType = -1;
            if (netInfo != null)
                networkType = netInfo.getType();

            isWiFi = networkType == ConnectivityManager.TYPE_WIFI;
            isMobile = networkType == ConnectivityManager.TYPE_MOBILE;
            NetworkInfo networkInfo = connectivityManager.getNetworkInfo(networkType);
            isConnected = false;
            if (networkInfo != null) {
                isConnected = networkInfo.isConnected();
                Log.d(TAG, networkInfo.toString());
            }
            return this;
        }
    }
}
