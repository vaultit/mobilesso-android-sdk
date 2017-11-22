package org.vaultit.mobilesso.mobilessosdk;


import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

import net.openid.appauth.AuthState;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;
import java.util.Objects;

import static org.vaultit.mobilesso.mobilessosdk.Preconditions.checkNotEmpty;

// Session class reflects ongoing state of session, not just a snapshot
public class Session {
    private static final String TAG = "Session";
    private static final Long clockSkewTolerance_ms = 120000L;
    private Data mData = null;
    private Context mContext = null;

    public enum SessionStatus {
        NO_SESSION,       // no tokens
        EXPIRED,          // tokens exist, but invalid
        VALID             // tokens exist and refresh token still valid
    }

    /**
     *
     * @param context  activity context
     */
    public Session(@NonNull Context context) {
        this.mData = Data.getInstance(context.getApplicationContext());
        this.mContext = context;
    }
    /**
     * Returns true if successfully authorized.  This means that
     * at least either an access token or an ID token have been retrieved.
     */
    public boolean isAuthorized() {
        return mData.getAuthState().isAuthorized();
    }

    public boolean isOnline() {
        return mData.getNetworkAvailable();
    }

    public String getAccessToken() {
        return (mData.getAuthState().getLastTokenResponse() != null ?
        mData.getAuthState().getLastTokenResponse().accessToken : null);
    }

    public String getRefreshToken() {
        return (mData.getAuthState().getLastTokenResponse() != null ?
                mData.getAuthState().getLastTokenResponse().refreshToken : null);
    }

    public String getIdToken() {
        return (mData.getAuthState().getLastTokenResponse() != null ?
                mData.getAuthState().getLastTokenResponse().idToken : null);
    }

    /**
     * Returns id token payload, which is stored on disk (see Data).
     * @return
     */
    public IdTokenPayload getIdTokenPayload() {

        return mData.getIdTokenPayload();
    }

    public String getScope() {
        return (mData.getAuthState().getLastTokenResponse() != null ?
                mData.getAuthState().getLastTokenResponse().scope : null);
    }

    public AuthState getAuthState() {
        return mData.getAuthState();
    }
    /**
     * Determines session status.  If value returned is VALID, then tokens are in usable state, ie.
     * refreshToken is valid and can be used to refresh the access token.  If value returned is
     * EXPIRED, then refresh token is expired, but browser's SSO session might still be valid,
     * in which case a new authentication is not needed, ie. perform session check.
     *
     * @return  returns one of: VALID | EXPIRED | NO_SESSION
     */
    public SessionStatus getStatus() {
        if (!mData.getAuthState().isAuthorized() || getIdTokenPayload() == null
                || mData.getAuthState().getAccessToken() == null) {
            return SessionStatus.NO_SESSION;
        }
        Date expiration = getIdTokenPayload().getExpirationTime();
        if (expiration != null) {
            Date now = new Date();
            if (now.getTime() > (expiration.getTime() + clockSkewTolerance_ms)) {
                return SessionStatus.EXPIRED;
            } else {
                return SessionStatus.VALID;
            }
        } else {
            return SessionStatus.NO_SESSION;
        }
    }

    boolean validate() {
        IdTokenPayload idTokenPayload = getIdTokenPayload();
        if (idTokenPayload == null || !idTokenPayload.isValid()) {
            Log.e(TAG,"validate(): failed. idTokenPayload.isValid=" +
                    (idTokenPayload != null ? idTokenPayload.isValid() : "null"));
            return false;
        }
        // TODO:  add checks for issuerURL ("iss") and clientID ("aud")
        Date now = new Date();
        boolean validToken = now.getTime() < (idTokenPayload.getExpirationTime().getTime() +
                clockSkewTolerance_ms) &&
                now.getTime() > ((idTokenPayload.getIssuedAtTime().getTime()) -
                clockSkewTolerance_ms);
        Log.d(TAG,"validate(): exp=" + idTokenPayload.getExpirationTime());
        return validToken;
    }

    public Session jsonDeserialize(@NonNull String jsonStr) throws JSONException {
        AuthState authState;
        checkNotEmpty(jsonStr, "jsonStr cannot be null or empty");
        authState = AuthState.jsonDeserialize(new JSONObject(jsonStr));
        return new Session(mContext);
    }

    public String jsonSerializeString() {
        return mData.getAuthState().jsonSerialize().toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || !(obj instanceof Session)) {
            return false;
        }
        Session other = (Session) obj;
        return Objects.equals(this.getAccessToken(),other.getAccessToken()) &&
               Objects.equals(this.getIdToken(),other.getIdToken()) &&
               Objects.equals(this.getRefreshToken(),other.getRefreshToken()) &&
               Objects.equals(this.jsonSerializeString(),other.jsonSerializeString());
    }

    // TODO : add hash()
}

