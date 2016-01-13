package com.enlightns.enlightns;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.OperationCanceledException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBarActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ExpandableListView;
import android.widget.TextView;
import android.widget.Toast;

import com.enlightns.enlightns.api.EnlightnsAPI;
import com.enlightns.enlightns.api.EnlightnsAPIHelper;
import com.enlightns.enlightns.auth.EnlightnsAccountAuthenticator;
import com.enlightns.enlightns.db.Record;
import com.enlightns.enlightns.settings.SettingsActivity;
import com.enlightns.enlightns.tasks.UpdateDBRecords;
import com.enlightns.enlightns.updater.NetworkChangeReceiver;
import com.enlightns.enlightns.utils.ExpandableListAdapter;
import com.enlightns.enlightns.utils.ListRefresh;
import com.enlightns.enlightns.utils.NetworkUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class RecordListing extends ActionBarActivity implements ListRefresh {

    private static final String TAG = RecordListing.class.getName();

    private static final String RECORDS_UPDATE_ACTION = "com.enlightns.enlightns.actions.RecordsUpdated";
    private static final String DISPLAY_IP_UPDATE_ACTION = "com.enlightns.enlightns.actions.IPAddressUpdated";

    private AccountManager mAccountManager;
    private ExpandableListView mListView;
    private TextView mCurrentIpTextView;
    private MenuItem mRefreshAction;

    private BroadcastReceiver mRecordsUpdateReceiver;
    private BroadcastReceiver mDisplayIPUpdateReceiver;

    public static void broadcastRecordsUpdated(Context context) {
        context.sendBroadcast(new Intent(RECORDS_UPDATE_ACTION));
    }

    public static void broadcastDisplayIPUpdate(Context context) {
        context.sendBroadcast(new Intent(DISPLAY_IP_UPDATE_ACTION));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_record_listing);

        PreferenceManager.setDefaultValues(this, R.xml.pref_general, false);

        mAccountManager = AccountManager.get(this);

        mCurrentIpTextView = (TextView) findViewById(R.id.current_ip);

        mListView = (ExpandableListView) findViewById(R.id.records_list);
        mListView.setChildDivider(getResources().getDrawable(R.drawable.empty));

        new GetUserRecordsTask().execute();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!accountExists()) {
            addNewAccount();
        }

        new GetDisplayIpTask(this).execute();

        refreshList();

        registerRecordsUpdateReceiver();
        registerDisplayIPUpdateReceiver();

    }

    @Override
    protected void onPause() {

        unregisterReceiver(mRecordsUpdateReceiver);
        unregisterReceiver(mDisplayIPUpdateReceiver);

        super.onPause();
    }

    private boolean accountExists() {
        return !"".equals(EnlightnsAccountAuthenticator.getAccountToken(getBaseContext()));
    }

    @Override
    public void refreshList() {
        List<String> headers = new ArrayList<>();
        Map<String, List<Record>> records = new HashMap<>();

        headers.add(getString(R.string.active_records_list_header));
        headers.add(getString(R.string.disabled_records_list_header));

        List<Record> activeRecords = Record.find(Record.class, "update_active = ?", "1");
        List<Record> disabledRecords = Record.find(Record.class, "update_active = ?", "0");

        records.put(headers.get(0), activeRecords);
        records.put(headers.get(1), disabledRecords);

        ExpandableListAdapter mExpListAdapter = new ExpandableListAdapter(this, headers, records, this);

        boolean[] expandedGroups = null;

        if (mListView.getAdapter() != null) {
            expandedGroups = new boolean[mExpListAdapter.getGroupCount()];
            for (int i = 0; i < expandedGroups.length; i++) {
                expandedGroups[i] = mListView.isGroupExpanded(i);
            }
        }

        mListView.setAdapter(mExpListAdapter);

        if (expandedGroups != null) {
            for (int i = 0; i < expandedGroups.length; i++) {
                if (expandedGroups[i]) {
                    mListView.expandGroup(i);
                }
            }
        } else {
            mListView.expandGroup(0);
        }
    }

    private void registerRecordsUpdateReceiver() {
        IntentFilter filter = new IntentFilter(RECORDS_UPDATE_ACTION);

        mRecordsUpdateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                refreshList();
            }
        };

        registerReceiver(mRecordsUpdateReceiver, filter);
    }

    private void registerDisplayIPUpdateReceiver() {
        IntentFilter filter = new IntentFilter(DISPLAY_IP_UPDATE_ACTION);

        mDisplayIPUpdateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                new GetDisplayIpTask(context).execute();
            }
        };

        registerReceiver(mDisplayIPUpdateReceiver, filter);
    }

    private void addNewAccount() {
        mAccountManager.addAccount(EnlightnsAccountAuthenticator.ACCOUNT_TYPE,
                EnlightnsAccountAuthenticator.AUTH_TOKEN_TYPE, null, null, this, new AccountManagerCallback<Bundle>() {
                    @Override
                    public void run(AccountManagerFuture<Bundle> future) {
                        try {
                            Bundle bnd = future.getResult();
                            showMessage(getString(R.string.successful_login_toast));
                            new GetUserRecordsTask().execute();
                            Log.d(TAG, "AddNewAccount Bundle is " + bnd);
                        } catch (OperationCanceledException oce) {
                            Log.d(TAG, "Operation cancelled, no account available, exiting...", oce);
                            finish();
                        } catch (Exception e) {
                            Log.w(TAG, "Exception", e);
                            showMessage(getString(R.string.login_error));
                        }
                    }
                }, null);
    }

    private void updateCredentials() {
        Account account = EnlightnsAccountAuthenticator.getAccount(mAccountManager);
        mAccountManager.updateCredentials(account, EnlightnsAccountAuthenticator.AUTH_TOKEN_TYPE,
                null, this, new AccountManagerCallback<Bundle>() {
                    @Override
                    public void run(AccountManagerFuture<Bundle> future) {
                        try {
                            Bundle bnd = future.getResult();
                            showMessage(getString(R.string.successful_login_toast));
                            Log.d(TAG, "UpdateCredentials Bundle is " + bnd);
                        } catch (OperationCanceledException e) {
                            Log.d(TAG, "Operation cancelled, no account available, exiting...", e);
                            showMessage(getString(R.string.account_needed_to_continue));
                            finish();
                        } catch (Exception e) {
                            Log.w(TAG, "Exception", e);
                            showMessage(getString(R.string.login_error));
                        }
                    }
                }, null);
    }

    private void removeAccount() {
        Account account = EnlightnsAccountAuthenticator.getAccount(mAccountManager);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            mAccountManager.removeAccount(account, this, new AccountManagerCallback<Bundle>() {
                @Override
                public void run(AccountManagerFuture<Bundle> future) {
                    try {
                        Bundle bnd = future.getResult();
                        showMessage("Account successfully removed");
                        Log.d(TAG, "RemoveAccount Bundle is " + bnd);
                        finish();
                        startActivity(new Intent(getApplicationContext(), RecordListing.class));
                    } catch (Exception e) {
                        Log.w(TAG, "Exception", e);
                        showMessage(getString(R.string.login_error));

                    }
                }
            }, null);
        } else {
            mAccountManager.removeAccount(account, new AccountManagerCallback<Boolean>() {
                @Override
                public void run(AccountManagerFuture<Boolean> future) {
                    try {
                        showMessage("Account successfully removed");
                        Log.d(TAG, "Removed account");
                        finish();
                        startActivity(new Intent(getApplicationContext(), RecordListing.class));
                    } catch (Exception e) {
                        Log.w(TAG, "Exception", e);
                        showMessage(getString(R.string.login_error));
                    }
                }
            }, null);
        }
    }

    private void showMessage(final String msg) {
        if (TextUtils.isEmpty(msg))
            return;

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getBaseContext(), msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_records_listing, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_refresh_records) {
            mRefreshAction = item;
            mRefreshAction.setActionView(R.layout.action_refresh);
            new GetUserRecordsTask().execute();
            return true;
        } else if (id == R.id.action_update_records) {
            Intent manualRecordUpdate = new Intent(NetworkChangeReceiver.MANUAL_UPDATE_ACTION);
            sendBroadcast(manualRecordUpdate);
            return true;
        } else if (id == R.id.action_logout) {
            removeAccount();
            return true;
        } else if (id == R.id.action_settings) {
            Intent settingsActivity = new Intent(this, SettingsActivity.class);
            startActivity(settingsActivity);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private class GetUserRecordsTask extends AsyncTask<Void, Void, List<EnlightnsAPI.ApiRecord>> {

        @Override
        protected List<EnlightnsAPI.ApiRecord> doInBackground(Void... params) {

            String authToken = EnlightnsAccountAuthenticator.getAccountToken(getBaseContext());

            if ("".equals(authToken)) {
                Log.d(TAG, "No account found, skipping");
                return null;
            }

            Log.d(TAG, "Auth Token: " + authToken);

            EnlightnsAPI ensApi = new EnlightnsAPIHelper().getEnlightnsAPI();

            List<EnlightnsAPI.ApiRecord> userRecords = null;
            try {
                userRecords = ensApi.getUserRecords(authToken, "A,AAAA");
            } catch (EnlightnsAPIHelper.ForbiddenException fe) {
                Log.d(TAG, "Forbidden, token might be invalid", fe);
                showMessage(getString(R.string.invalid_credentials_login_prompt));
                updateCredentials();
                finish();
            } catch (Exception e) {
                Log.w(TAG, "Network error while getting user records", e);
            }

            return userRecords;
        }

        @Override
        protected void onPostExecute(List<EnlightnsAPI.ApiRecord> apiRecords) {
            if (apiRecords != null && !apiRecords.isEmpty()) {
                UpdateDBRecords.updateRecords(apiRecords);
            }

            if (mRefreshAction != null)
                mRefreshAction.setActionView(null);

            refreshList();

        }
    }

    private class GetDisplayIpTask extends AsyncTask<Void, Void, String> {

        private final Context mContext;

        public GetDisplayIpTask(Context context) {
            mContext = context;
        }

        @Override
        protected String doInBackground(Void... params) {

            String ip = null;

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
            boolean useLanIp = prefs.getBoolean(mContext.getString(R.string.use_lan_ip_key), false);

            if (useLanIp && NetworkUtils.isConnectedToWifi(mContext)) {
                ip = NetworkUtils.getLanIp(mContext);
            } else {
                EnlightnsAPI ensApi = new EnlightnsAPIHelper().getEnlightnsAPI();
                try {
                    ip = ensApi.getWanIp().ip;
                } catch (Exception e) {
                    Log.w(TAG, "Network error while getting WAN IP", e);
                }
            }

            return ip;
        }

        @Override
        protected void onPostExecute(String wanIp) {
            if (wanIp != null) {
                mCurrentIpTextView.setText(wanIp);
            } else {
                mCurrentIpTextView.setText(getString(R.string.ip_na));
            }
        }
    }

}
