package org.vaultit.mobilesso.mobilessosdk;


import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;
import android.util.Log;

import net.openid.appauth.AuthorizationException;
import net.openid.appauth.AuthorizationException.GeneralErrors;
import net.openid.appauth.AuthorizationException.AuthorizationRequestErrors;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;

import static net.openid.appauth.AuthorizationException.TYPE_OAUTH_AUTHORIZATION_ERROR;
import static net.openid.appauth.AuthorizationException.TYPE_OAUTH_TOKEN_ERROR;
import static org.vaultit.mobilesso.mobilessosdk.Preconditions.checkNotEmpty;
import static org.vaultit.mobilesso.mobilessosdk.SessionError.ErrorCode.AUTHORIZATION_NETWORK_ERROR;
import static org.vaultit.mobilesso.mobilessosdk.SessionError.ErrorCode.AUTHORIZATION_OATH_ERROR;
import static org.vaultit.mobilesso.mobilessosdk.SessionError.ErrorCode.AUTHORIZATION_SERVER_ERROR;
import static org.vaultit.mobilesso.mobilessosdk.SessionError.ErrorCode.SESSION_REFRESH_NETWORK_ERROR;
import static org.vaultit.mobilesso.mobilessosdk.SessionError.ErrorCode.SESSION_REFRESH_OAUTH_ERROR;
import static org.vaultit.mobilesso.mobilessosdk.SessionError.ErrorCode.SESSION_REFRESH_SERVER_ERROR;
import static org.vaultit.mobilesso.mobilessosdk.SessionError.ErrorCode.UNKNOWN_ERROR;

public class SessionError {
    private static final String TAG = "SessionError";
    private final ErrorCode errorCode;
    private final String message;
    public AuthorizationException authException;

    @VisibleForTesting
    static final String KEY_CODE = "mobsso_code";
    @VisibleForTesting
    static final String KEY_MESSAGE = "mobsso_type";

    /**
     * All possible errors that can occur while managing sessions.
     */
    public enum ErrorCode {

        // The service configuration (ie. discovery doc) could not be loaded.
        INIT_SERVICE_CONFIG_LOAD_ERROR,

        // The auth state could not be parsed from the server response. This indicates a bug in either the
        // client or server code.  Currently not used.
        INIT_AUTH_STATE_PARSE_ERROR,

        // network not available
        INIT_NETWORK_ERROR,

        // expired session
        INIT_EXPIRED_SESSION_ERROR,

        // The session could not be refreshed because no session exists.
        INIT_NO_SESSION_ERROR,

        // authorize() failed b/c library not initialized
        AUTHORIZATION_NOT_INITIALIZED_ERROR,

        // server error during authorization
        AUTHORIZATION_SERVER_ERROR,

        // network error during authorization
        AUTHORIZATION_NETWORK_ERROR,

        // OAuth error during authorization
        AUTHORIZATION_OATH_ERROR,

        // Token exchange request failed during authorization
        AUTHORIZATION_TOKEN_REQUEST_ERROR,

        // The ID token did not validate.
        AUTHORIZATION_ID_TOKEN_VALIDATE_ERROR,

        // The session could not be refreshed because session is expired (refresh token
        // not valid).
        SESSION_REFRESH_EXPIRED_SESSION_ERROR,

        // The session could not be refreshed because no session exists.
        SESSION_REFRESH_NO_SESSION_ERROR,

        // The server rejected the refresh token. The refresh token is most probably expired.
        // To re-login the user, use the *presentLogin* method of SessionManager.
        SESSION_REFRESH_OAUTH_ERROR,

        // Network error occurred (e.g. no internet connection) while refreshing the session.
        SESSION_REFRESH_NETWORK_ERROR,

        // Server returned an error while refreshing the session. This might indicate a server issue.
        SESSION_REFRESH_SERVER_ERROR,

        // The service configuration did not contain an URL for ending the session.
        LOGOUT_ERROR_NO_END_SESSION_URL_ERROR,

        // No session to log out
        LOGOUT_ERROR_NO_SESSION_ERROR,

        // Network error occurred (e.g. no internet connection) while trying to log out.
        LOGOUT_ERROR_NETWORK_ERROR,

        // Server returned an error while logging out. This might indicate a server issue.
        LOGOUT_ERROR_SERVER_ERROR,

        // logout cannot be performed, SessionManager not initialized
        LOGOUT_ERROR_NOT_INITIALIZED_ERROR,

        // No idea what happened.
        UNKNOWN_ERROR
    }

    public SessionError(@NonNull ErrorCode code, @NonNull String message) {
        this.errorCode = code;
        this.message = message;
    }
    public SessionError(@NonNull ErrorCode code,  @NonNull String message,
                              @NonNull AuthorizationException ex) {
        this.errorCode = code;
        this.message = message;
        this.authException = ex;
    }

    public final ErrorCode getErrorCode() { return errorCode; }
    public final String getErrorMessage() { return message; }

    // prioritizes code over type
    static ErrorCode getErrorCode(AuthorizationException ex, String type) {
        if (ex.code == GeneralErrors.SERVER_ERROR.code ||
                ex.code == AuthorizationRequestErrors.SERVER_ERROR.code ||
                ex.code == AuthorizationRequestErrors.TEMPORARILY_UNAVAILABLE.code) {
            return (type.equals("refresh") ? SESSION_REFRESH_SERVER_ERROR :
                    AUTHORIZATION_SERVER_ERROR);
        } else if (ex.code == GeneralErrors.NETWORK_ERROR.code) {
            return (type.equals("refresh") ? SESSION_REFRESH_NETWORK_ERROR :
                    AUTHORIZATION_NETWORK_ERROR);
        } else if (ex.type == TYPE_OAUTH_AUTHORIZATION_ERROR ||
                ex.type == TYPE_OAUTH_TOKEN_ERROR) {
            return (type.equals("refresh") ? SESSION_REFRESH_OAUTH_ERROR :
                    AUTHORIZATION_OATH_ERROR);
        } else {
            return UNKNOWN_ERROR;
        }
    }
    public static ErrorCode getSessionRefreshErrorCode(AuthorizationException ex) {
        return getErrorCode(ex,"refresh");
    }

    public static ErrorCode getAuthorizationErrorCode(AuthorizationException ex) {
        return getErrorCode(ex,"authorization");
    }

    public static SessionError jsonDeserialize(@NonNull String jsonStr) throws JSONException {
        AuthorizationException ex = null;
        SessionError sessionError = null;
        checkNotEmpty(jsonStr, "jsonStr cannot be null or empty");
        JSONObject json = new JSONObject(jsonStr);
        try {
            ex = AuthorizationException.fromJson(json);
        } catch (JSONException e) {
            sessionError =  new SessionError(Enum.valueOf(ErrorCode.class, json.getString(KEY_CODE)),
                    json.getString(KEY_MESSAGE));
        }
        if (ex != null) {
            sessionError = new SessionError(Enum.valueOf(ErrorCode.class, json.getString(KEY_CODE)),
                    json.getString(KEY_MESSAGE), ex);
        }
        Log.d(TAG,"SessionError(): code=" + errorCode2string(sessionError.getErrorCode()) +
             " message=" + sessionError.getErrorMessage() +
             " ex=" + (ex != null ? ex.toString() : "null"));
        return sessionError;
    }

    public String jsonSerializeString() {
        JSONObject json;
        Log.d(TAG,"jsonSerializeString(): code=" + errorCode2string(getErrorCode()) +
                " message=" + getErrorMessage() +
                " ex=" + (authException != null ? authException.toString() : "null"));
        if (authException != null) {
            json = authException.toJson();
        } else {
            json = new JSONObject();
        }
        try {
            json.put(KEY_CODE,errorCode);
            json.put(KEY_MESSAGE,message);
        } catch (JSONException ex) {
            Log.e(TAG,"jsonSerializeString() : JSON exception", ex);
        }
        return json.toString();
    }
    
    public static String errorCode2string(ErrorCode code) {
        switch (code) {
        case INIT_SERVICE_CONFIG_LOAD_ERROR:
            return "INIT_SERVICE_CONFIG_LOAD_ERROR";
        case INIT_AUTH_STATE_PARSE_ERROR:
            return "INIT_AUTH_STATE_PARSE_ERROR";
        case INIT_NETWORK_ERROR:
            return "INIT_NETWORK_ERROR";
        case AUTHORIZATION_SERVER_ERROR:
            return "AUTHORIZATION_SERVER_ERROR";
        case AUTHORIZATION_NETWORK_ERROR:
            return "AUTHORIZATION_NETWORK_ERROR";
        case AUTHORIZATION_OATH_ERROR:
            return "AUTHORIZATION_OATH_ERROR";
        case AUTHORIZATION_TOKEN_REQUEST_ERROR:
            return "AUTHORIZATION_TOKEN_REQUEST_ERROR";
        case AUTHORIZATION_ID_TOKEN_VALIDATE_ERROR:
            return "AUTHORIZATION_ID_TOKEN_VALIDATE_ERROR";
        case SESSION_REFRESH_NO_SESSION_ERROR:
            return "SESSION_REFRESH_NO_SESSION_ERROR";
        case SESSION_REFRESH_OAUTH_ERROR:
            return "SESSION_REFRESH_OAUTH_ERROR";
        case SESSION_REFRESH_NETWORK_ERROR:
            return "SESSION_REFRESH_NETWORK_ERROR";
        case SESSION_REFRESH_SERVER_ERROR:
            return "SESSION_REFRESH_SERVER_ERROR";
        case LOGOUT_ERROR_NO_END_SESSION_URL_ERROR:
            return "LOGOUT_ERROR_NO_END_SESSION_URL_ERROR";
        case LOGOUT_ERROR_NETWORK_ERROR:
            return "LOGOUT_ERROR_NETWORK_ERROR";
        case LOGOUT_ERROR_SERVER_ERROR:
            return "LOGOUT_ERROR_SERVER_ERROR";
        case UNKNOWN_ERROR:
            return "UNKNOWN_ERROR";
        default:
            return "internalError";
        }
    }
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || !(obj instanceof SessionError)) {
            return false;
        }
        SessionError other = (SessionError) obj;
        return this.errorCode == other.errorCode && this.message.equals(other.message) &&
                Objects.equals(this.authException,other.authException);
    }
}
