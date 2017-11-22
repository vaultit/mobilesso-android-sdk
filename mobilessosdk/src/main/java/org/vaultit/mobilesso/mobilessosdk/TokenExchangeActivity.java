package org.vaultit.mobilesso.mobilessosdk;


import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.MainThread;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import net.openid.appauth.AuthorizationException;
import net.openid.appauth.AuthorizationResponse;
import net.openid.appauth.AuthorizationService;
import net.openid.appauth.ClientAuthentication;
import net.openid.appauth.ClientSecretBasic;
import net.openid.appauth.TokenRequest;
import net.openid.appauth.TokenResponse;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.vaultit.mobilesso.mobilessosdk.SessionError.ErrorCode.AUTHORIZATION_ID_TOKEN_VALIDATE_ERROR;
import static org.vaultit.mobilesso.mobilessosdk.SessionError.ErrorCode.AUTHORIZATION_TOKEN_REQUEST_ERROR;


public class TokenExchangeActivity extends AppCompatActivity {
    private static final String TAG = "TokenExchangeActivity";
    private static final AtomicLong NEXT_ID = new AtomicLong(0);
    private final long id = NEXT_ID.getAndIncrement();
    private AuthorizationService mAuthService = null;
    private Data mData = null;
    private Context mContext = null;

    @Override
    protected void onCreate(Bundle savedInstanceState)  {
        super.onCreate(savedInstanceState);
        Log.d(TAG,"onCreate() id=" + id);
        mContext = this;
        mData = Data.getInstance(mContext.getApplicationContext());
/*        AppAuthConfiguration appAuthConfig = new AppAuthConfiguration.Builder()
                .setBrowserMatcher(new BrowserWhitelist(
                        VersionedBrowserMatcher.CHROME_CUSTOM_TAB))
                .build();*/
        mAuthService = new AuthorizationService(mContext/*, appAuthConfig*/);
    }
    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG,"onStart() id=" + id);

        AuthorizationResponse response = AuthorizationResponse.fromIntent(getIntent());
        AuthorizationException ex = AuthorizationException.fromIntent(getIntent());

        if (response != null && response.authorizationCode != null) {
            Log.d(TAG, "onStart(): Received AuthorizationResponse (ie. code value)");
            mData.getAuthState().update(response, ex);
            mData.saveData();
            exchangeAuthorizationCode(response,
                    new ClientSecretBasic(mData.getIdp().getClientSecret()));
        } else if (ex != null) {
            Log.d(TAG,"onStart(): Authorization flow failed: " + ex.getMessage());
            Intent intent = mData.getTokenResponseIntent();
            SessionError sessionError = new SessionError(SessionError.getAuthorizationErrorCode(ex),
                    "Authorization flow failed ", ex);
            intent.putExtra(SessionManager.KEY_SESSION_ERROR_JSON,
                    sessionError.jsonSerializeString());
            startActivity(intent);
            finish();
        } else {
            Log.e(TAG,"onStart(): Internal error, no auth response or error");
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG,"onStop() id=" + id);
    }

    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG,"onDestroy() id=" + id);
        mAuthService.dispose();
        mAuthService = null;
    }

    public long getId() {
        return id;
    }
    private void exchangeAuthorizationCode(AuthorizationResponse authorizationResponse,
                                           ClientAuthentication clientAuth) {
        Log.d(TAG,"exchangeAuthorizationCode()");
        performCodeExchangeRequest(authorizationResponse.createTokenExchangeRequest(), clientAuth,
                new AuthorizationService.TokenResponseCallback() {
                    @Override
                    public void onTokenRequestCompleted(
                            @Nullable TokenResponse tokenResponse,
                            @Nullable AuthorizationException ex) {
                        handleCodeExchangeResponse(tokenResponse, ex);
                    }
                });
    }

    @MainThread
    private void performCodeExchangeRequest(
            TokenRequest request,
            ClientAuthentication clientAuth,
            AuthorizationService.TokenResponseCallback callback) {

        mAuthService.performTokenRequest(
                request,
                clientAuth,
                callback);
    }

    private void sendErrorIntent(Intent intent, SessionError.ErrorCode code, String message,
                                 AuthorizationException authException) {
        SessionError sessionError = new SessionError(code, message, authException);
        intent.putExtra(SessionManager.KEY_SESSION_ERROR_JSON,
                sessionError.jsonSerializeString());
        startActivity(intent);
    }

    @MainThread
    private void handleCodeExchangeResponse(
            @Nullable TokenResponse tokenResponse,
            @Nullable AuthorizationException authException) {

        mData.getAuthState().update(tokenResponse, authException);
        // read and reset tokenResponseIntent
        Intent intent = mData.getTokenResponseIntent();
        mData.setTokenResponseIntent(null);
        // reset & renew idTokenPayload
        mData.setIdTokenPayload(null);
        IdTokenPayload idTokenPayload = mData.getIdTokenPayload();
        mData.saveData();

        if (authException != null) {
            Log.d(TAG,"Authorization Code exchange failed " + authException.error);
            sendErrorIntent(intent, AUTHORIZATION_TOKEN_REQUEST_ERROR, "Authorization code exchange failed ",
                    authException);
        } else {
            Session session = new Session(mContext);
            Log.d(TAG,"Authorization Code exchange successful; accessToken=" + session.getAccessToken());
            if (session.validate()) {
                // send any login notifications that have been registered for
                Map<Long,NotificationReceiver> receivers = mData.getNotificationReceivers();
                for (Map.Entry<Long, NotificationReceiver> entry : receivers.entrySet()) {
                    entry.getValue().sendEvent(NotificationReceiver.EventType.LOGIN_COMPLETE);
                }
                // session object not included in intent; the recipient must call
                // SessionManager.getSession() for it
                startActivity(intent);
            } else {
                Log.e(TAG,"Error: Session cannot be validated");
                sendErrorIntent(intent, AUTHORIZATION_ID_TOKEN_VALIDATE_ERROR, "The ID token was rejected",
                        authException);
            }
        }
        finish();
    }
}
