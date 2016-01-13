package com.enlightns.enlightns.auth;

import android.accounts.Account;
import android.accounts.AccountAuthenticatorActivity;
import android.accounts.AccountManager;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.enlightns.enlightns.R;
import com.enlightns.enlightns.api.EnlightnsAPI;
import com.enlightns.enlightns.api.EnlightnsAPIHelper;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class EnlightnsAccountAuthenticatorActivity extends AccountAuthenticatorActivity {

    public static final String KEY_EXTRA_USER_PASS = "extra_user_pass";
    public static final String KEY_ERROR_MESSAGE = "error_message";
    public static final String ARG_IS_ADDING_NEW_ACCOUNT = "is_adding_new_account";
    public static final String ARG_ACCOUNT_TYPE = "account_type";
    public static final String ARG_AUTH_TYPE = "auth_type";
    public static final String ARG_ACCOUNT_NAME = "account_name";
    public static final Pattern VALID_EMAIL_ADDRESS_REGEX =
            Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,6}$", Pattern.CASE_INSENSITIVE);
    private final static String TAG = EnlightnsAccountAuthenticatorActivity.class.getName();
    private UserLoginTask mAuthTask = null;

    // UI references.
    private EditText mEmailView;
    private EditText mPasswordView;
    private View mProgressView;
    private View mLoginFormView;

    private AccountManager mAccountManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "Activity onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_enlightns_account_authenticator);

        mAccountManager = AccountManager.get(getBaseContext());

        // Set up the login form.
        mEmailView = (EditText) findViewById(R.id.email);

        if (!TextUtils.isEmpty(getIntent().getStringExtra(ARG_ACCOUNT_NAME))) {
            mEmailView.setText(getIntent().getStringExtra(ARG_ACCOUNT_NAME));
        }

        mPasswordView = (EditText) findViewById(R.id.password);
        mPasswordView.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int id, KeyEvent keyEvent) {
                if (id == R.id.login || id == EditorInfo.IME_NULL) {
                    attemptLogin();
                    return true;
                }
                return false;
            }
        });

        Button mSignInButton = (Button) findViewById(R.id.sign_in_button);
        mSignInButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                attemptLogin();
            }
        });

        mLoginFormView = findViewById(R.id.login_form);
        mProgressView = findViewById(R.id.login_progress);

        TextView createAccountLink = (TextView) findViewById(R.id.create_account_link);
        createAccountLink.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                String signUpLink = getString(R.string.signup_link);
                Intent signUpIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(signUpLink));
                startActivity(signUpIntent);
            }
        });
    }

    @Override
    public void onBackPressed() {
        finish();
    }

    /**
     * Attempts to sign in or register the account specified by the login form.
     * If there are form errors (invalid email, missing fields, etc.), the
     * errors are presented and no actual login attempt is made.
     */
    public void attemptLogin() {

        Log.d(TAG, "Activity attemptLogin");

        if (mAuthTask != null) {
            return;
        }

        // Reset errors.
        mEmailView.setError(null);
        mPasswordView.setError(null);

        // Store values at the time of the login attempt.
        String email = mEmailView.getText().toString();
        String password = mPasswordView.getText().toString();

        boolean cancel = false;
        View focusView = null;


        // Check for a valid password, if the user entered one.
        if (!TextUtils.isEmpty(password) && !isPasswordValid(password)) {
            mPasswordView.setError(getString(R.string.error_invalid_password));
            focusView = mPasswordView;
            cancel = true;
        }

        // Check for a valid email address.
        if (TextUtils.isEmpty(email)) {
            mEmailView.setError(getString(R.string.error_field_required));
            focusView = mEmailView;
            cancel = true;
        } else if (!isEmailValid(email)) {
            mEmailView.setError(getString(R.string.error_invalid_email));
            focusView = mEmailView;
            cancel = true;
        }

        if (cancel) {
            // There was an error; don't attempt login and focus the first
            // form field with an error.
            focusView.requestFocus();
        } else {
            // Show a progress spinner, and kick off a background task to
            // perform the user login attempt.
            showProgress(true);
            mAuthTask = new UserLoginTask(this, email, password);
            mAuthTask.execute((Void) null);
        }
    }

    private boolean isEmailValid(String email) {
        Matcher matcher = VALID_EMAIL_ADDRESS_REGEX.matcher(email);
        return matcher.find();
    }

    private boolean isPasswordValid(String password) {
        return password.length() > 4;
    }

    public void showProgress(final boolean show) {
        int shortAnimTime = getResources().getInteger(android.R.integer.config_shortAnimTime);

        mLoginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
        mLoginFormView.animate().setDuration(shortAnimTime).alpha(
                show ? 0 : 1).setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mLoginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
            }
        });

        mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
        mProgressView.animate().setDuration(shortAnimTime).alpha(
                show ? 1 : 0).setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
            }
        });
    }

    private void finishLogin(Intent intent) {
        String accName = intent.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
        String accPass = intent.getStringExtra(KEY_EXTRA_USER_PASS);
        String accType = intent.getStringExtra(AccountManager.KEY_ACCOUNT_TYPE);
        String authToken = intent.getStringExtra(AccountManager.KEY_AUTHTOKEN);

        final Account account = new Account(accName, accType);
        if (getIntent().getBooleanExtra(ARG_IS_ADDING_NEW_ACCOUNT, false)) {
            mAccountManager.addAccountExplicitly(account, accPass, null);
        } else {
            mAccountManager.setPassword(account, accPass);
        }

        mAccountManager.setAuthToken(account, EnlightnsAccountAuthenticator.AUTH_TOKEN_TYPE, authToken);

        setAccountAuthenticatorResult(intent.getExtras());
        setResult(RESULT_OK, intent);
        finish();
    }

    public class UserLoginTask extends AsyncTask<Void, Void, Intent> {

        private final String mEmail;
        private final String mPassword;
        private final Context mContext;

        UserLoginTask(Context context, String email, String password) {
            mEmail = email;
            mPassword = password;
            mContext = context;
        }

        @Override
        protected Intent doInBackground(Void... params) {

            Log.d(TAG, "UserLoginTask doInBackground");


            EnlightnsAPI ensApi = new EnlightnsAPIHelper().getEnlightnsAPI();
            String jwtToken;

            try {
                EnlightnsAPI.Token authToken = ensApi.getAuthToken(new EnlightnsAPI.AuthUser(mEmail, mPassword));
                jwtToken = authToken.token;
            } catch (EnlightnsAPIHelper.BadRequestException bre) {
                Log.w(TAG, "Invalid credentials", bre);
                final Intent res = new Intent();
                res.putExtra(KEY_ERROR_MESSAGE, mContext.getString(R.string.invalid_credentials));
                return res;
            } catch (Exception e) {
                Log.w(TAG, "Network error while getting user info", e);
                final Intent res = new Intent();
                res.putExtra(KEY_ERROR_MESSAGE, getString(R.string.auth_server_communication_error));
                return res;
            }

            Log.d(TAG, "UserLoginTask got token: " + jwtToken);

            final Intent res = new Intent();
            res.putExtra(AccountManager.KEY_ACCOUNT_NAME, mEmail);
            res.putExtra(AccountManager.KEY_ACCOUNT_TYPE, EnlightnsAccountAuthenticator.ACCOUNT_TYPE);
            res.putExtra(AccountManager.KEY_AUTHTOKEN, jwtToken);
            res.putExtra(KEY_EXTRA_USER_PASS, mPassword);

            return res;
        }

        @Override
        protected void onPostExecute(final Intent intent) {

            stopLoginTask();

            if (intent.getStringExtra(KEY_ERROR_MESSAGE) == null) {
                finishLogin(intent);
            } else {
                Toast.makeText(mContext, intent.getStringExtra(KEY_ERROR_MESSAGE), Toast.LENGTH_LONG).show();
            }
        }

        @Override
        protected void onCancelled() {
            stopLoginTask();
        }

        protected void stopLoginTask() {
            mAuthTask = null;
            showProgress(false);
        }
    }
}



