package com.enlightns.enlightns.auth;

import android.accounts.AbstractAccountAuthenticator;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.accounts.NetworkErrorException;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;

import com.enlightns.enlightns.R;
import com.enlightns.enlightns.api.EnlightnsAPI;
import com.enlightns.enlightns.api.EnlightnsAPIHelper;
import com.enlightns.enlightns.db.Record;

public class EnlightnsAccountAuthenticator extends AbstractAccountAuthenticator {

    public static final String ACCOUNT_TYPE = "com.enlightns.enlightns";
    public static final String AUTH_TOKEN_TYPE = "Records";
    public static final String AUTH_TOKEN_TYPE_LABEL = "Listing and update of records on the EnlightNS account.";
    private static final String TAG = EnlightnsAccountAuthenticator.class.getName();
    private final Context mContext;

    public EnlightnsAccountAuthenticator(Context context) {
        super(context);
        this.mContext = context;
    }

    public static String getAccountToken(Context context) {

        AccountManager accountManager = AccountManager.get(context);

        Account account = getAccount(accountManager);

        if (account == null) {
            return "";
        }

        return accountManager.peekAuthToken(account, AUTH_TOKEN_TYPE);
    }

    public static Account getAccount(AccountManager accountManager) {
        Account[] accounts = accountManager.getAccountsByType(ACCOUNT_TYPE);
        Account account = null;
        if (accounts.length > 0) {
            account = accounts[0];
        }
        return account;
    }

    @Override
    public Bundle editProperties(AccountAuthenticatorResponse response, String accountType) {
        return null;
    }

    @Override
    public Bundle addAccount(AccountAuthenticatorResponse response, String accountType, String authTokenType, String[] requiredFeatures, Bundle options) throws NetworkErrorException {
        Log.d(TAG, "Auth addAccount");
        final Intent intent = new Intent(mContext, EnlightnsAccountAuthenticatorActivity.class);
        intent.putExtra(EnlightnsAccountAuthenticatorActivity.ARG_ACCOUNT_TYPE, accountType);
        intent.putExtra(EnlightnsAccountAuthenticatorActivity.ARG_AUTH_TYPE, authTokenType);
        intent.putExtra(EnlightnsAccountAuthenticatorActivity.ARG_IS_ADDING_NEW_ACCOUNT, true);
        intent.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response);
        final Bundle bundle = new Bundle();
        bundle.putParcelable(AccountManager.KEY_INTENT, intent);
        return bundle;
    }

    @Override
    public Bundle confirmCredentials(AccountAuthenticatorResponse response, Account account, Bundle options) throws NetworkErrorException {
        return null;
    }

    @Override
    public Bundle getAuthToken(AccountAuthenticatorResponse response, Account account, String authTokenType, Bundle options) throws NetworkErrorException {
        // Extract the username and password from the Account Manager, and ask
        // the server for an appropriate AuthToken.

        Log.d(TAG, "Auth getAuthToken");

        if (!authTokenType.equals(AUTH_TOKEN_TYPE)) {
            final Bundle result = new Bundle();
            result.putString(AccountManager.KEY_ERROR_MESSAGE, "Invalid authTokenType!");
            return result;
        }

        final AccountManager am = AccountManager.get(mContext);

        String authToken = am.peekAuthToken(account, authTokenType);

        // Lets give another try to authenticate the user
        if (TextUtils.isEmpty(authToken)) {
            final String password = am.getPassword(account);
            if (password != null) {
                EnlightnsAPI ensApi = new EnlightnsAPIHelper().getEnlightnsAPI();

                try {
                    final EnlightnsAPI.Token ensToken = ensApi.getAuthToken(new EnlightnsAPI.AuthUser(account.name, password));
                    authToken = ensToken.token;

                } catch (Exception e) {
                    Log.w(TAG, "Network error while getting user info", e);
                    final Bundle result = new Bundle();
                    result.putString(AccountManager.KEY_ERROR_MESSAGE,
                            mContext.getString(R.string.auth_server_communication_error));
                    return result;
                }
            }
        }

        // If we get an authToken - we return it
        if (!TextUtils.isEmpty(authToken)) {
            final Bundle result = new Bundle();
            result.putString(AccountManager.KEY_ACCOUNT_NAME, account.name);
            result.putString(AccountManager.KEY_ACCOUNT_TYPE, account.type);
            result.putString(AccountManager.KEY_AUTHTOKEN, authToken);
            return result;
        }

        // If we get here, then we couldn't access the user's password - so we
        // need to re-prompt them for their credentials. We do that by creating
        // an intent to display our AuthenticatorActivity.
        final Intent intent = new Intent(mContext, EnlightnsAccountAuthenticatorActivity.class);
        intent.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response);
        intent.putExtra(EnlightnsAccountAuthenticatorActivity.ARG_ACCOUNT_TYPE, account.type);
        intent.putExtra(EnlightnsAccountAuthenticatorActivity.ARG_AUTH_TYPE, authTokenType);
        intent.putExtra(EnlightnsAccountAuthenticatorActivity.ARG_ACCOUNT_NAME, account.name);
        final Bundle bundle = new Bundle();
        bundle.putParcelable(AccountManager.KEY_INTENT, intent);
        return bundle;
    }

    @Override
    public String getAuthTokenLabel(String authTokenType) {
        return AUTH_TOKEN_TYPE_LABEL;
    }

    @Override
    public Bundle updateCredentials(AccountAuthenticatorResponse response, Account account, String authTokenType, Bundle options) throws NetworkErrorException {
        final Intent authIntent = new Intent(mContext, EnlightnsAccountAuthenticatorActivity.class);
        authIntent.putExtra(EnlightnsAccountAuthenticatorActivity.ARG_ACCOUNT_NAME, account.name);
        authIntent.putExtra(EnlightnsAccountAuthenticatorActivity.ARG_AUTH_TYPE, authTokenType);
        authIntent.putExtra(EnlightnsAccountAuthenticatorActivity.ARG_IS_ADDING_NEW_ACCOUNT, false);
        final Bundle bundle = new Bundle();
        bundle.putParcelable(AccountManager.KEY_INTENT, authIntent);
        return bundle;
    }

    @Override
    public Bundle hasFeatures(AccountAuthenticatorResponse response, Account account, String[] features) throws NetworkErrorException {
        return null;
    }

    @Override
    @NonNull
    public Bundle getAccountRemovalAllowed(AccountAuthenticatorResponse response, Account account)
            throws NetworkErrorException {
        Bundle result = super.getAccountRemovalAllowed(response, account);

        if (result.containsKey(AccountManager.KEY_BOOLEAN_RESULT) &&
                !result.containsKey(AccountManager.KEY_INTENT)) {
            final boolean removalAllowed = result.getBoolean(AccountManager.KEY_BOOLEAN_RESULT);

            if (removalAllowed) {
                Record.deleteAll(Record.class);
            }
        }

        return result;
    }
}
