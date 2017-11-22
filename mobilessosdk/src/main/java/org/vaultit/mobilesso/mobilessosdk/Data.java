package org.vaultit.mobilesso.mobilessosdk;


import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.gson.JsonSyntaxException;

import net.openid.appauth.AuthState;
import net.openid.appauth.AuthorizationServiceConfiguration;
import net.openid.appauth.AuthorizationServiceDiscovery;

import org.json.JSONException;

import java.lang.ref.WeakReference;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

class Data {
    private static final String TAG = "Data";
    private static WeakReference<Data> sInstance = new WeakReference<>(null);
    // constants for saving state on disk
    static final String FILE_SAVED_APP_STATE = "mobileSsoSdk_savedState";
    private static final String KEY_AUTH_STATE_JSON = "authStateInJson";
    private static final String KEY_ID_TOKEN_PAYLOAD_JSON = "idTokenPayloadInJson";
    private static final String KEY_TOKEN_RESPONSE_INTENT = "tokenResponseIntent";
    private static final String KEY_LOGOUT_RESPONSE_INTENT = "logoutResponseIntent";
    private static final String KEY_LOGOUT_ENDPOINT = "logoutEndpoint";
    private static final String KEY_IDP_JSON = "idpInJson";
    private static final String KEY_SM_INITIALIZED = "smInitialized";
    private static final String KEY_SM_INIT_ONGOING = "smInitOngoing";
    private static final String KEY_NETWORK_AVAILABLE = "networkAvailable";

    // constant for discovery doc
    private static final String DISCOVERY_DOC_LOGOUT_ENDPOINT = "end_session_endpoint";

    // AppAuth library entity which contains all authorization state info, stored on disk
    private AuthState mAuthState = null;

    // id token payload, generated from id token
    // stored to disk as 1st class member, since it appears that sometimes
    // id token is not available
    private IdTokenPayload mIdTokenPayload = null;

    // identity provider info;
    private IdentityProvider mIdp = null;

    // end point uri where logout is performed
    private Uri mLogoutEndPoint = null;
    // intent for returning to caller after authorization is done
    private Intent mTokenResponseIntent = null;
    private Intent mLogoutResponseIntent = null;
    private boolean mSmInitOngoing= false;
    private boolean mNetworkAvailable = true;

    private Context mAppContext;

    // non-persistent data
    private final Set<SessionManager.SessionListener> mListeners =
            new HashSet<>();
    private final Map<Long,ConnectivityChangeReceiver> mConnChangeReceivers =
            new HashMap<Long, ConnectivityChangeReceiver>();
    private final Map<Long,NotificationReceiver> mNotificationReceivers =
            new HashMap<Long, NotificationReceiver>();

    /**
     * Constructor which creates a singleton.
     * If singleton already exists, will read contents from permanent store.
     *
     * @param application context  needed for reading/writing to permanent store
     * @return singleton instance
     */
    static synchronized Data getInstance(Context appContext) {
        Log.d(TAG,"getInstance()");
        Data data = sInstance.get();

        if (data == null) {
            Log.d(TAG,"getInstance(): creating 1st instance");
            data = new Data();
            sInstance = new WeakReference<>(data);
            data.mAppContext = appContext;
            data.readData();
        }

        if (data.mAuthState == null) {
            data.mAuthState = new AuthState();
        }

        return data;
    }

    private Data() {}

    /**
     * Init for adding idp info.
     * @param idp identity provider
     */
    synchronized void init(@NonNull IdentityProvider idp) {
        Log.d(TAG,"init()");
        mIdp = idp;
        saveData();
    }

    /**
     * resets session data, also from permanent store
     *   mainly access-, refresh- and id-tokens.
     */
    synchronized void sessionReset() {
        Log.d(TAG,"sessionReset()");

        // retain discovery doc if it exists
        AuthorizationServiceConfiguration asc = mAuthState.getAuthorizationServiceConfiguration();
        if (asc != null) {
            mAuthState = new AuthState(asc);
        } else {
            mAuthState = new AuthState();
        }

        SharedPreferences.Editor editor = mAppContext.getSharedPreferences(FILE_SAVED_APP_STATE,
                Context.MODE_PRIVATE).edit();
        // delete all
        editor.clear();
        editor.apply();

        // retain idp & logoutendpt
        if (mIdp != null) {
            editor.putString(KEY_IDP_JSON, mIdp.serializeToJson());
        }
        if (mLogoutEndPoint != null) {
            editor.putString(KEY_LOGOUT_ENDPOINT, mLogoutEndPoint.toString());
        }

        // retains only discovery doc
        editor.putString(KEY_AUTH_STATE_JSON, mAuthState.jsonSerializeString());
        editor.apply();

        mTokenResponseIntent = null;
        mIdTokenPayload = null;
    }

    // deletes all data from disk & almost all from memory;
    // left in memory: mAppContext  (needed for opening data file later)
    synchronized void dataReset(Context context) {
        Log.d(TAG,"dataReset()");
        SharedPreferences.Editor editor = context.getSharedPreferences(Data.FILE_SAVED_APP_STATE,
                Context.MODE_PRIVATE).edit();
        editor.clear();
        editor.commit();
        mAuthState = new AuthState();
        mLogoutEndPoint = null;
        mLogoutResponseIntent = null;
        mIdTokenPayload = null;
        mTokenResponseIntent = null;
        mIdp = null;
        mNetworkAvailable = false;  // default value
        mSmInitOngoing = false;     // default value
        mListeners.clear();
        mNotificationReceivers.clear();

        for(ConnectivityChangeReceiver receiver: mConnChangeReceivers.values()) {
            Context ctx = receiver.getActivityContext();
            ctx.unregisterReceiver(receiver);
        }
        mConnChangeReceivers.clear();
    }

    // deletes data from disk, can be called without Data object instance since static
    synchronized static void diskDataReset(Context context) {
        Log.d(TAG,"diskDataReset()");
        SharedPreferences.Editor editor = context.getSharedPreferences(Data.FILE_SAVED_APP_STATE,
                Context.MODE_PRIVATE).edit();
        editor.clear();
        editor.commit();
    }

    synchronized AuthState getAuthState() {
        return mAuthState;
    }
    synchronized void setAuthState(@NonNull AuthState authState) {
        mAuthState = authState;
    }

    synchronized IdTokenPayload getIdTokenPayload() {
        // if null, try to reconstruct from id token
        if (mIdTokenPayload == null) {
            Log.d(TAG,"getIdTokenPayload(): reconstructing idTokenPayload from id token");
            String json = IdTokenPayload.decodeJWTToken(getIdToken());
            if (json != null) {
                mIdTokenPayload = new IdTokenPayload(json);
                if (!mIdTokenPayload.isValid()) {
                    Log.e(TAG,"getIdTokenPayload(): error deserializing id token!");
                    mIdTokenPayload = null;
                } else {
                    saveData();  // save new idtokenpayload to disk
                }
            } else {
                Log.e(TAG,"getIdTokenPayload(): missing id token!");
            }
        }
        return mIdTokenPayload;
    }

    private String getIdToken() {
        return (getAuthState().getLastTokenResponse() != null ?
                getAuthState().getLastTokenResponse().idToken : null);
    }

    synchronized void setIdTokenPayload(IdTokenPayload idTokenPayload) {
        this.mIdTokenPayload = idTokenPayload;
    }

    synchronized IdentityProvider getIdp() {
        return mIdp;
    }
    synchronized Uri getLogoutEndPoint() {
        return mLogoutEndPoint;
    }
    synchronized void setLogoutEndPoint(Uri logoutEndPoint) {
        this.mLogoutEndPoint = logoutEndPoint;
    }
    synchronized Intent getTokenResponseIntent() {
        return mTokenResponseIntent;
    }
    synchronized void setTokenResponseIntent(Intent tokenResponseIntent) {
        this.mTokenResponseIntent = tokenResponseIntent;
    }
    synchronized Intent getLogoutResponseIntent() {
        return mLogoutResponseIntent;
    }
    synchronized void setLogoutResponseIntent(Intent logoutResponseIntent) {
        this.mLogoutResponseIntent = logoutResponseIntent;
    }

    synchronized Set<SessionManager.SessionListener> getListeners() {
        return mListeners;
    }

    synchronized Map<Long,ConnectivityChangeReceiver> getConnChangeReceivers() {
        return mConnChangeReceivers;
    }

    synchronized Map<Long, NotificationReceiver> getNotificationReceivers() {
        return mNotificationReceivers;
    }

    synchronized static Uri getLogoutEndPointFromDoc(
            @NonNull AuthorizationServiceDiscovery discoveryDoc) {
        Uri endpoint = null;
        try {
            endpoint = Uri.parse(discoveryDoc.docJson.getString(DISCOVERY_DOC_LOGOUT_ENDPOINT));
        } catch (JSONException ex) {
            Log.e(TAG,"getLogoutEndPoint(): JSON problem when reading from discovery doc");
        }
        return endpoint;
    }
    synchronized boolean getInitOngoing() {
        return mSmInitOngoing;
    }
    synchronized void setInitOngoing(boolean initOngoing) {
        this.mSmInitOngoing = initOngoing;
    }
    synchronized boolean getNetworkAvailable() {
        return mNetworkAvailable;
    }
    synchronized void setNetworkAvailable(boolean available) {
        this.mNetworkAvailable = available;
    }

    /**
     *   read local data from permanent storage.  Classes:
     *      AuthState
     *      IdTokenPayload
     *      IdentityProvider
     *      LogoutEndpoint
     *      tokenResponseIntent
     *      logoutResponseIntent
     *      smInitOngoing
     */
    synchronized void readData() {
        SharedPreferences appPrefs = mAppContext.getSharedPreferences(FILE_SAVED_APP_STATE,
                Context.MODE_PRIVATE);
        String authStateJson = appPrefs.getString(KEY_AUTH_STATE_JSON, null);
        String idTokenPayloadJson = appPrefs.getString(KEY_ID_TOKEN_PAYLOAD_JSON, null);
        String logoutUri = appPrefs.getString(KEY_LOGOUT_ENDPOINT, null);
        String idpJson = appPrefs.getString(KEY_IDP_JSON, null);
        String tokResp = appPrefs.getString(KEY_TOKEN_RESPONSE_INTENT, null);
        String logoutIntent = appPrefs.getString(KEY_LOGOUT_RESPONSE_INTENT, null);
        mSmInitOngoing = appPrefs.getBoolean(KEY_SM_INIT_ONGOING, false);
        mNetworkAvailable = appPrefs.getBoolean(KEY_NETWORK_AVAILABLE, true);

        if (logoutUri != null) {
            mLogoutEndPoint = Uri.parse(logoutUri);
        }
        if (authStateJson != null) {
            Log.d(TAG, "readData(): authStateJson=" + authStateJson);
            try {
                mAuthState = AuthState.jsonDeserialize(authStateJson);
            } catch (JSONException ex) {
                Log.e(TAG, "readData(): Malformed AuthState JSON saved", ex);
            }
        }
        if (idTokenPayloadJson != null) {
            Log.d(TAG, "readData(): idTokenPayloadJson=" + idTokenPayloadJson);
            mIdTokenPayload = new IdTokenPayload(idTokenPayloadJson);
            if (!mIdTokenPayload.isValid()) {
                mIdTokenPayload = null;
                Log.e(TAG, "readData(): Malformed idTokenPayload JSON saved");
            }
        }
        if (idpJson != null) {
            try {
                mIdp = IdentityProvider.deserializeFromJson(idpJson);
            } catch (JsonSyntaxException ex) {
                Log.e(TAG, "readData(): Malformed IDP JSON saved", ex);
            }
        }
        if (tokResp != null) {
            try {
                mTokenResponseIntent = Intent.parseUri(tokResp, Intent.URI_INTENT_SCHEME);
            } catch (URISyntaxException ex) {
                Log.e(TAG,"readData(): Malformed token response intent");
            }
        }
        if (logoutIntent != null) {
            try {
                mLogoutResponseIntent = Intent.parseUri(logoutIntent, Intent.URI_INTENT_SCHEME);
            } catch (URISyntaxException ex) {
                Log.e(TAG,"readData(): Malformed logout response intent");
            }
        }
        Log.d(TAG, "readData() : AuthState=" +
                (authStateJson != null ? authStateJson.length() : "0") +
                " idTokPay=" + (idTokenPayloadJson != null ? idTokenPayloadJson.length() : "0") +
                " idp=" + (idpJson != null ? idpJson.length() : "0") +
                " tokenResp="  + (tokResp != null ? tokResp.length() : "0") +
                " logoutIntent="  + (logoutIntent != null ? logoutIntent.length() : "0") +
                " logoutUri=" + (logoutUri != null ? logoutUri.length() : "0") +
                " initOngoing=" + mSmInitOngoing +
                " nwAvail=" + mNetworkAvailable);
    }

    /**
     *   save local data to permanent storage.  Classes:
     *      AuthState
     *      IdTokenPayload
     *      IdentityProvider
     *      LogoutEndpoint
     *      tokenResponseIntent
     *      logoutResponseIntent
     *      smInitOngoing
     */
    synchronized void saveData() {
        int authL = 0, idTokPayL = 0, idpL = 0, tokL = 0, logoutIL = 0, logoutL = 0;
        String str;
        SharedPreferences.Editor editor = mAppContext.getSharedPreferences(FILE_SAVED_APP_STATE,
                Context.MODE_PRIVATE).edit();

        if (mAuthState != null) {
            str = mAuthState.jsonSerializeString();
            authL = str.length();
            editor.putString(KEY_AUTH_STATE_JSON, str);
        }
        if (mIdTokenPayload != null) {
            str = mIdTokenPayload.serializeToJson();
            idTokPayL = str.length();
            editor.putString(KEY_ID_TOKEN_PAYLOAD_JSON, str);
        }
        if (mIdp != null) {
            str = mIdp.serializeToJson();
            idpL = str.length();
            editor.putString(KEY_IDP_JSON, str);
        }
        if (mLogoutEndPoint != null) {
            str = mLogoutEndPoint.toString();
            logoutL = str.length();
            editor.putString(KEY_LOGOUT_ENDPOINT, str);
        }
        if (mTokenResponseIntent != null) {
            str = mTokenResponseIntent.toUri(Intent.URI_INTENT_SCHEME);
            tokL = str.length();
            editor.putString(KEY_TOKEN_RESPONSE_INTENT,str);
        }
        if (mLogoutResponseIntent != null) {
            str = mLogoutResponseIntent.toUri(Intent.URI_INTENT_SCHEME);
            logoutIL = str.length();
            editor.putString(KEY_LOGOUT_RESPONSE_INTENT,str);
        }
        editor.putBoolean(KEY_SM_INIT_ONGOING, mSmInitOngoing);
        editor.putBoolean(KEY_NETWORK_AVAILABLE, mNetworkAvailable);
        editor.apply();
        Log.d(TAG, "saveData(): AuthState=" + authL +
                " idTokPay=" + idTokPayL + " idp=" + idpL +
                " tokenResp=" + tokL + " logoutIntent=" + logoutIL + " logoutUri=" + logoutL +
                " initOngoing=" + mSmInitOngoing +
                " nwAvail=" + mNetworkAvailable);
    }

}
