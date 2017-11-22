package org.vaultit.mobilesso.mobilessosdk;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import net.openid.appauth.AuthState;
import net.openid.appauth.AuthorizationException;
import net.openid.appauth.AuthorizationRequest;
import net.openid.appauth.AuthorizationService;
import net.openid.appauth.AuthorizationServiceConfiguration;
import net.openid.appauth.ClientSecretBasic;
import net.openid.appauth.ResponseTypeValues;
import net.openid.appauth.TokenResponse;

import org.vaultit.mobilesso.mobilessosdk.SessionError.ErrorCode;
import org.vaultit.mobilesso.mobilessosdk.Util.ConnectionUtilities;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Main class for Mobile SSO SDK
 *  - manages sessions, normal call sequence:
 *
 *      addSessionListener()
 *      initialize()
 *      authorize()
 *      ...
 *      getFreshSession()
 *      <some network operation using access token>
 *      ...
 *      logout()   // if required
 *      removeSessionListener()
 *
 */
public class SessionManager {
    // Static
    private static final AtomicLong NEXT_ID = new AtomicLong(0);

    public static final String KEY_SESSION_ERROR_JSON = "SessionManager_keySessionErrorJson";

    private static final String TAG = "SessionManager";

    // Members
    private final long id = NEXT_ID.getAndIncrement();
    private static Long mNextClientId = 0L;

    // Intents
    private static final String INTENT_EXTRA_AUTH_CALLBACK = "authorizationCallback";

    // AppAuth library entity which contains all authorization state info, stored on disk
    private AuthorizationService mAuthService = null;
    private LogoutService mLogoutService = null;

    private Data mData = null;
    private Context mContext = null; // Activity context
    private Long mHandle;  // this identifies SessionManager uniquely and ties it to context

    /**
     * Constructs new SessionManager object, which includes idp in data part.
     */
    public SessionManager(@NonNull Context context, @NonNull IdentityProvider idp) {
        mHandle = getNextClientId();
        Log.d(TAG,"constructor with context & idp id=" + getId() +
            " handle=" + mHandle);
        mData = Data.getInstance(context.getApplicationContext());
        mData.init(idp);
        mContext = context;
/*        AppAuthConfiguration appAuthConfig = new AppAuthConfiguration.Builder()
                .setBrowserMatcher(new BrowserWhitelist(
                        VersionedBrowserMatcher.CHROME_CUSTOM_TAB))
                .build();*/
        mAuthService = new AuthorizationService(context/*, appAuthConfig*/);
        mLogoutService = new LogoutService(context);
    }

    SessionManager(@NonNull Context context) {
        Log.d(TAG,"constructor id=" + getId());
        mHandle = getNextClientId();
        mData = Data.getInstance(context.getApplicationContext());
        mContext = context;
        if (mData.getIdp() == null) {
            Log.e(TAG,"Internal error: data not initialized properly");
        }
        mAuthService = new AuthorizationService(context);
        mLogoutService = new LogoutService(context);
    }

    private long getId() {
        return id;
    }
    private synchronized Long getNextClientId() {
        mNextClientId += 1;
        return mNextClientId;
    }
    /**
     *  Is initialized? This means that initialize() has been successfully called, ie.
     *  that the discovery document has been downloaded
     * @return Returns true if initialized.
     */
    public boolean isInitialized() {
        return mData.getIdp() != null &&
                mData.getAuthState().getAuthorizationServiceConfiguration() != null;
    }

    /**
     * Should be called prior to deleting the object (e.g. in Activity's onDestroy()).
     */
    public void dispose() {
        mData = null;
        mAuthService.dispose();
        mAuthService = null;
        mLogoutService.dispose();
        mLogoutService = null;
    }

    /**
     * Returns current session.
     * @return Session
     */
    public Session getSession() {
        Session session = new Session(mContext);
        return session;
    }

    /**
     * Authorizes client, including performing code exchange for tokens.
     * If successful, returns intent given as parameter.
     *
     *   - will check if network status has changed and return didLoseNetwork or didGainNetwork, no
     *   further work will be done if network is unavailable; if network is not available, will
     *   return didFailAuthorize
     *   - will check if initialized, if not will send didLoseSession
     *
     * @param activityContext      activity context
     * @param tokenResponseIntent  Intent that will be used to start an activity after
     *                             processing is done.  The returned intent contains a SessionError
     *                             object if an error occurred.
     */
    public void authorize(
            @NonNull Context activityContext,
            @NonNull Intent tokenResponseIntent) {
        authorize(activityContext,tokenResponseIntent,null);
    }

    /**
     * Authorizes client, including performing code exchange for tokens.
     * If successful, returns intent given as parameter without SessionError object.
     *
     *   - will check if network status has changed and return didLoseNetwork or didGainNetwork, no
     *   further work will be done if network is unavailable; if network is not available, will
     *   return didFailAuthorize
     *   - will check if initialized, if not will send didLoseSession
     *
     * @param activityContext      activity context
     * @param tokenResponseIntent  Intent that will be used to start an activity after
     *                             processing is done.  The returned intent contains a SessionError
     *                             object if an error occurred.
     * @param additionalParams     Additional parameters for authorization request, e.g. prompt,
     *                             acr_values
     */
    public void authorize(
            @NonNull Context activityContext,
            @NonNull Intent tokenResponseIntent,
            @Nullable Map<String,String> additionalParams) {
        Log.d(TAG,"authorize() id=" + getId());
        AuthorizationRequest authRequest;

        if (!checkNetwork(null)) {
            SessionError.ErrorCode code = ErrorCode.AUTHORIZATION_NETWORK_ERROR;
            SessionError error = new SessionError(code, "Cannot authorize: No network connection");
            failAuthorizeWithError(error);
            return;
        }

        if (!checkInitialized(null)) {
            SessionError.ErrorCode code = ErrorCode.AUTHORIZATION_NOT_INITIALIZED_ERROR;
            SessionError error = new SessionError(code,
                    "Cannot authorize: SessionManager is not initialized");
            failAuthorizeWithError(error);
            return;
        }

        if (additionalParams == null) {
            additionalParams = new HashMap<>();
        }

        // intent for returning from auth flow (after code exchange for tokens)
        mData.setTokenResponseIntent(tokenResponseIntent);
        mData.saveData();

        // create pending intent for intermediate result
        Intent intent = new Intent(activityContext, TokenExchangeActivity.class);
        intent.putExtra(INTENT_EXTRA_AUTH_CALLBACK, true);
        PendingIntent intermedPendIntent = PendingIntent.getActivity(activityContext, 0, intent,0);

        // AppAuth requires that prompt parameter is set using AuthorizationRequest.Builder.setPrompt()
        String promptVal = null;
        Map<String,String> extraParams = new HashMap<String, String>();
        for (Map.Entry<String, String> entry : additionalParams.entrySet()) {
            if (entry.getKey().equals("prompt")) {
                promptVal = entry.getValue();
            } else {
                extraParams.put(entry.getKey(),entry.getValue());
            }
        }

        AuthorizationServiceConfiguration authServiceConfig = mData.getAuthState().getAuthorizationServiceConfiguration();

        authRequest = new AuthorizationRequest.Builder(
                authServiceConfig,
                mData.getIdp().getClientId(),
                ResponseTypeValues.CODE,
                mData.getIdp().getRedirectUri())
                .setScope(mData.getIdp().getScope())
                .setPrompt(promptVal)
                .setAdditionalParameters(extraParams)
                .build();

        Log.d(TAG, "authorize(): Making auth request to " +
            mData.getAuthState().getAuthorizationServiceConfiguration().authorizationEndpoint +
                " prompt=" + (promptVal != null ? promptVal : "null") +
                " redirectUri=" + mData.getIdp().getRedirectUri() +
                " clientId=" + mData.getIdp().getClientId() +
                " scopes=" + mData.getIdp().getScope() +
                " extraParams=" + extraParams.toString());

        mAuthService.performAuthorizationRequest(
                authRequest,
                intermedPendIntent,
                mAuthService.createCustomTabsIntentBuilder()
                        .build());
    }

    private void failAuthorizeWithError(@NonNull final SessionError error) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                for (SessionListener listener : mData.getListeners()) {
                    Log.d(TAG,"failAuthorizeWithError(): calling didFailAuthorize");
                    listener.didFailAuthorize(error);
                }
            }
        });
    }

    private void failLogoutWithError(@NonNull final SessionError error) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                for (SessionListener listener : mData.getListeners()) {
                    Log.d(TAG,"failLogoutWithError(): calling didFailLogout");
                    listener.didFailLogout(error);
                }
            }
        });
    }

    /**
     * initializes library.
     * - will check if network status has changed and return didLoseNetwork or didGainNetwork
     *   in addition to SessionListener.initialized().   initialized() callback can still return
     *   success (ie. a session) in offline case if refresh token is still valid
     * - will read any previous  session state from file
     * - if discovery doc missing, will fetch again
     * - will refresh tokens if needed
     *
     * Callback will be through SessionListener.initialized(session,error).  If successful, ie.
     * session is valid (refresh token ok), session will be returned, otherwise error.
     * Caller should check if session is online or not before continuing.  Caller should consider
     * offline case where stale (EXPIRED) session exists, to show possible cached user data anyway.
     *
     * This function must be called after calling logout(), since logout clears the initialized
     * data.
     */
    public void initialize() {
        Log.d(TAG,"initialize() starting id=" + getId());

        // sanity check
        if (mData.getListeners().isEmpty()) {
            Log.e(TAG,"Client App Error: no listeners for SessionManager initialize!");
        }

        mData.readData();
        mData.setInitOngoing(true);
        mData.saveData();

        // first handle case where no network is available
        if (!checkNetwork(null)) {
            // check for case where previous session exists and refresh token is still valid
            AuthState authState = mData.getAuthState();
            if (authState != null) {
                Session session = new Session(mContext);
                if (authState.getAuthorizationServiceConfiguration() != null &&
                    authState.isAuthorized() &&
                    authState.getAccessToken() != null &&
                    session.getStatus() == Session.SessionStatus.VALID) {

                    callListenerOnInitializeResult(session, null);
                } else {
                    initFailNoNetwork();
                }
            } else {
                initFailNoNetwork();
            }
            return;
        }

        if (mData.getAuthState().getAuthorizationServiceConfiguration() == null) {
            fetchDiscoveryDoc();
        } else {
            postDiscDocFetchLogic();
        }
    }

    private void postDiscDocFetchLogic() {
        Session session = new Session(mContext);
        if (mData.getAuthState().isAuthorized() &&
                mData.getAuthState().getAccessToken() != null) {
            if (session.getStatus() == Session.SessionStatus.VALID) {
                if (mData.getAuthState().getNeedsTokenRefresh()) {
                    refreshTokens(null);
                } else {
                    callListenerOnInitializeResult(session, null);
                }
            } else {
                // session is expired
                callListenerOnInitializeResult(null,
                        new SessionError(ErrorCode.INIT_EXPIRED_SESSION_ERROR,
                                "Cannot refresh session because expired session"));
            }
        } else {
            callListenerOnInitializeResult(null,
                    new SessionError(ErrorCode.INIT_NO_SESSION_ERROR,
                            "Cannot refresh session because no previous session"));
        }
    }

    private void initFailNoNetwork() {
        // no network exists and no valid session exists
        SessionError.ErrorCode code = ErrorCode.INIT_NETWORK_ERROR;
        SessionError error = new SessionError(code, "Cannot init: No network connection");
        callListenerOnInitializeResult(null, error);
    }

    private void fetchDiscoveryDoc() {
        Log.d(TAG,"fetchDiscoveryDoc()");
        AuthorizationServiceConfiguration.fetchFromUrl(
            mData.getIdp().getDiscoveryEndpoint(),
            new AuthorizationServiceConfiguration.RetrieveConfigurationCallback() {

                @Override
                public void onFetchConfigurationCompleted(
                        @Nullable AuthorizationServiceConfiguration serviceConfiguration,
                        @Nullable AuthorizationException ex) {
                    if (ex != null) {
                        Log.e(TAG,"Failed to retrieve discovery document: " + ex.getMessage());
                        callListenerOnInitializeResult(null,
                            new SessionError(ErrorCode.INIT_SERVICE_CONFIG_LOAD_ERROR,
                                    "Could not load service configuration", ex));
                        return;
                    }
                    Log.d(TAG,"fetchDiscoveryDoc()  success");
                    //noinspection ConstantConditions
                    mData.setAuthState(new AuthState(serviceConfiguration));
                    //noinspection ConstantConditions
                    mData.setLogoutEndPoint(Data.getLogoutEndPointFromDoc(
                            serviceConfiguration.discoveryDoc));
                    mData.saveData();
                    postDiscDocFetchLogic();
                }
            });
    }

    private void refreshTokens(@Nullable final TokenRefreshCallback callback) {
        Log.d(TAG,"refreshTokens()");
        mAuthService.performTokenRequest(
            mData.getAuthState().createTokenRefreshRequest(),
            new ClientSecretBasic(mData.getIdp().getClientSecret()),
            new AuthorizationService.TokenResponseCallback() {
                @Override
                public void onTokenRequestCompleted(
                        @Nullable TokenResponse response,
                        @Nullable final AuthorizationException ex) {
                    mData.getAuthState().update(response, ex);
                    mData.saveData();

                    if (ex != null) {
                        Log.d(TAG,"refreshTokens(): failed to refresh tokens");
                        callListenerOnInitializeResult(null,
                                new SessionError(SessionError.getSessionRefreshErrorCode(ex),
                                        "failed to refresh tokens",ex));
                    } else {
                        Session session = new Session(mContext);
                        Log.d(TAG,"refreshTokens(): success, accessToken=" + session.getAccessToken());
                        callListenerOnInitializeResult(session, null);
                    }
                    if (callback != null) {
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            public void run() {
                                Session session = null;
                                SessionError error = null;

                                if (ex == null) {
                                    session = new Session(mContext);
                                }
                                else {
                                    error = new SessionError(
                                            SessionError.getSessionRefreshErrorCode(ex),
                                            "failed to refresh tokens", ex);
                                }

                                callback.tokenRefreshCallback(session, error);
                            }
                        });
                    }
                }
            });
    }

    /**
     * Will fetch fresh tokens if needed.  Tokens are access, id and refresh tokens.
     *
     *   - will check if network status has changed and return didLoseNetwork or didGainNetwork, no
     *   further work will be done if network is unavailable
     *   - will check if initialized, if not will send didLoseSession and return callback with error
     *
     * @param callback will return session or error.  Generic listeners will also return
     *                 session status or error.
     */
    public void getFreshSession(final TokenRefreshCallback callback) {
        Log.d(TAG,"getFreshSession()");
        if (!checkNetwork(callback)) {
            return;
        }

        if (!checkInitialized(callback)) {
            return;
        }

        if (mData.getAuthState().getNeedsTokenRefresh()) {
            refreshTokens(callback);
        } else {
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                public void run() {
                    Session session = new Session(mContext);
                    callback.tokenRefreshCallback(session, null);
                }
            });
        }
    }

    /**
     * Will always try to refresh tokens.  Similar to getFreshSession().
     *
     *   - will check if network status has changed and return didLoseNetwork or didGainNetwork, no
     *   further work will be done if network is unavailable
     *   - will check if initialized, if not will send didLoseSession and return callback with error
     *
     * @param callback will return session or error.  Generic listeners will also return
     *                 session status or error.
     */
    public void refreshSession(final TokenRefreshCallback callback) {
        Log.d(TAG, "refreshSession() id=" + getId());

        if (!checkNetwork(callback)) {
            return;
        }

        if (!checkInitialized(callback)) {
            return;
        }

        refreshTokens(callback);
    }

    /**
     *  Will reset session and other state (e.g. tokens, discovery doc)
     */
    public void sessionReset() {
        Log.d(TAG,"sessionReset() id=" + getId());
        mData.sessionReset();
    }

    /**
     *  Will reset all data.  This can be used when no activities exist.
     */
    public static void dataReset(Context context) {
        Log.d(TAG,"dataReset()");
        Data.diskDataReset(context);
    }

    /**
     * performs logout request, using browser.
     *
     *   - will check if network status has changed and return didLoseNetwork or didGainNetwork, no
     *   further work will be done if network is unavailable
     *   - will check if initialized, if not will send didLoseSession
     *
     * The function cannot be certain if the logout operation will succeed (e.g. race condition
     * with another app).  In case of failure, the browser will be left open with an error
     * message.  To maximize the likelihood of success, the function will refresh tokens prior
     * to attempting logout.
     *
     * After calling logout, the application must reinitialize the SSO library by calling
     * initialize().
     *
     * @param intent  callback intent for notifying of successful logout
     */
    public void logout(final Intent intent) {
        Log.d(TAG, "performLogoutRequest():  calling logoutService id=" + getId());

        if (!checkNetwork(null)) {
            SessionError.ErrorCode code = ErrorCode.LOGOUT_ERROR_NETWORK_ERROR;
            SessionError error = new SessionError(code, "Cannot logout: No network connection");
            failLogoutWithError(error);
            return;
        }

        if (!checkInitialized(null)) {
            SessionError.ErrorCode code = ErrorCode.LOGOUT_ERROR_NOT_INITIALIZED_ERROR;
            SessionError error = new SessionError(code,
                    "Cannot logout: SessionManager is not initialized");
            failLogoutWithError(error);
            return;
        }

        Session session = new Session(mContext);
        if (mData.getAuthState().getRefreshToken() == null || !session.validate()) {
            SessionError.ErrorCode code = SessionError.ErrorCode.LOGOUT_ERROR_NO_SESSION_ERROR;
            final SessionError error = new SessionError(code, "Logout failed: Cannot refresh session");
            failLogoutWithError(error);
            // also send generic didLoseSession
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    for (SessionListener listener : mData.getListeners()) {
                        listener.didLoseSession(error);
                    }
                }
            });

            return;
        }

        mAuthService.performTokenRequest(
            mData.getAuthState().createTokenRefreshRequest(),
            new ClientSecretBasic(mData.getIdp().getClientSecret()),
            new AuthorizationService.TokenResponseCallback() {
                @Override
                public void onTokenRequestCompleted(
                        @Nullable TokenResponse response,
                        @Nullable AuthorizationException ex) {
                    if (ex != null) {
                        Log.e(TAG, "logout refreshSession failed err=" + ex.getMessage());
                        mData.sessionReset();
                    } else {
                        Log.d(TAG, "logout refreshSession ok");
                        //noinspection ConstantConditions
                        mData.getAuthState().update(response, ex);

                        //noinspection ConstantConditions
                        mLogoutService.performLogoutRequest(mData.getAuthState().getIdToken(),
                                mData.getIdp(),
                                mData.getLogoutEndPoint(),
                                intent,
                                mAuthService.createCustomTabsIntentBuilder()
                                        .build());
                    }
                }
            });

    }
    // checks network status and returns didLoseNetwork or didGainNetwork callback if status has
    // changed;  if network is lost, specific callback will also return error;
    // returns value true if network available
    private boolean checkNetwork(@Nullable final TokenRefreshCallback callback) {
        if (!ConnectionUtilities.isNetworkAvailable(mContext)) {

            if (callback != null) {
                // always return error with supplied callback
                Log.d(TAG,"checkNetwork(): sending tokenRefreshCallback with no network error");
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    public void run() {
                        callback.tokenRefreshCallback(null,
                                new SessionError(ErrorCode.SESSION_REFRESH_NETWORK_ERROR,
                                        "Cannot perform action because network was lost"));
                    }
                });
            }
            if (mData.getNetworkAvailable()) {
                mData.setNetworkAvailable(false);

                // return error only when network loss determined the first time
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    public void run() {
                        for (SessionListener listener : mData.getListeners()) {
                            Log.d(TAG, "checkNetwork(): calling  didLoseNetwork");
                            listener.didLoseNetwork();
                        }
                    }
                });
            }

        } else {
            if (!mData.getNetworkAvailable()) {
                mData.setNetworkAvailable(true);

                // return network regained message only if previously lost
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    public void run() {
                        for (SessionListener listener : mData.getListeners()) {
                            Log.d(TAG, "checkNetwork(): calling  didGainNetwork");
                            listener.didGainNetwork();
                        }
                    }
                });
            }
        }
        return mData.getNetworkAvailable();
    }

    // checks initialized status and returns error through any callback and session listener if
    // not initialized
    // returns false if not initialized, true otherwise
    private boolean checkInitialized(@Nullable final TokenRefreshCallback callback) {
        if (!isInitialized()) {
            if (callback != null) {
                Log.d(TAG,"checkInitialized(): sending tokenRefreshCallback with no_session_error");
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    public void run() {
                        callback.tokenRefreshCallback(null,
                                new SessionError(ErrorCode.SESSION_REFRESH_NO_SESSION_ERROR,
                                        "Cannot refresh session because no previous session"));

                    }
                });
            }
            callListenerOnInitializeResult(null,
                    new SessionError(ErrorCode.SESSION_REFRESH_NO_SESSION_ERROR,
                            "Cannot refresh session because no previous session"));
            return false;
        }
        return true;
    }
    private void callListenerOnInitializeResult(final Session session, final SessionError error) {

        new Handler(Looper.getMainLooper()).post(new Runnable() {
            public void run() {
                if (mData.getInitOngoing()) {
                    mData.setInitOngoing(false);
                    mData.saveData();
                    for (SessionListener listener : mData.getListeners()) {
                        Log.d(TAG,"callListenerOnInitializeResult(): calling initialized");
                        listener.initialized(session, error);
                    }
                } else {
                    if (mData.getListeners().isEmpty()) {
                        Log.e(TAG,"callListenerOnInitializeResult()  no listeners!");
                    }
                    if (session != null) {
                        for (SessionListener listener : mData.getListeners()) {
                            Log.d(TAG,"callListenerOnInitializeResult(): calling didRefreshSession");
                            listener.didRefreshSession(session);
                        }
                    } else {
                        for (SessionListener listener : mData.getListeners()) {
                            Log.d(TAG,"callListenerOnInitializeResult(): calling didLoseSession");
                            listener.didLoseSession(error);
                        }
                    }
                }
            }
        });
    }

    /**
     * For adding a SessionListener to SessionManager, put in e.g. Activity's onStart().
     * Listeners will receive generic session state callbacks.
     * @param listener
     */
    public synchronized void addSessionListener(Context context) {
        Log.d(TAG,"addSessionListener() handle=" + mHandle);
        mData.getListeners().add((SessionListener)context);

        ConnectivityChangeReceiver connReceiver = new ConnectivityChangeReceiver(context, mData);
        Map<Long,ConnectivityChangeReceiver> receivers = mData.getConnChangeReceivers();
        if (receivers.containsKey(mHandle)) {
            Log.e(TAG,"addSessionListener(): internal error, using same handle");
        }
        try {
            receivers.put(mHandle, connReceiver);
            context.registerReceiver(connReceiver,
                    new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
        } catch (Exception ex) {
            Log.e(TAG,"addSessionListener(): exception when adding receiver" , ex);
        }
    }

    /**
     * For removing SessionListener.  Put in e.g. Activity's onStop().
     *
     * @param listener
     */
    public synchronized void removeSessionListener(SessionListener listener) {
        Log.d(TAG,"removeSessionListener()  handle=" + mHandle);
        mData.getListeners().remove(listener);

        // remove connectivity receiver for this handle
        Map<Long,ConnectivityChangeReceiver> receivers = mData.getConnChangeReceivers();
        if (receivers.containsKey(mHandle)) {
            try {
                Context context = receivers.get(mHandle).getActivityContext();
                context.unregisterReceiver(receivers.get(mHandle));
                receivers.remove(mHandle);
            }
            catch (IllegalArgumentException ex) {
                Log.w(TAG, "Attempting to unregister a connection change receiver that was not registered.");
            }
        } else {
            Log.w(TAG,"removeSessionListener():  called with unknown handle!");
        }
    }

    /**
     * allows Activity to register for events such as login and logout
     * @param event
     */
    public synchronized void registerForEvent(NotificationReceiver.EventType event) {
        Log.d(TAG, "registerForEvent(): handle=" + mHandle + " event="  + event);
        Map<Long,NotificationReceiver> receivers = mData.getNotificationReceivers();
        if (receivers.containsKey(mHandle)) {
            // receiver for this session manager instance already exists
            EnumSet<NotificationReceiver.EventType> events = receivers.get(mHandle).getEvents();
            events.add(event);
        } else {
            EnumSet<NotificationReceiver.EventType> events = EnumSet.of(event);
            NotificationReceiver receiver = new NotificationReceiver(mContext,events);
            receivers.put(mHandle,receiver);
        }
    }

    /**
     * allows Activity to unregister all events it is currently registered to receive;
     * place e.g. in onDestroy()
     */
    public synchronized void unregisterAllEvents() {
        Log.d(TAG,"unregisterAllEvents() handle=" + mHandle);
        Map<Long,NotificationReceiver> receivers = mData.getNotificationReceivers();
        if (receivers.containsKey(mHandle)) {
            receivers.remove(mHandle);
        }
    }
    /**
     * SessionListener interface for getting callbacks from SessionManager on session state.
     * - Use addSessionListener() / removeSessionListener() for activating all callbacks,
     * except notification().
     * - For notification() callback, activate using registerForEvent() / unregisterAllEvents()
     */
    public interface SessionListener {

        /**
         * Callback for initialize().  On success, will contain a valid session, otherwise
         * a SessionError.  Returned session may be in offline state, however.
         * @param session
         * @param error
         */
        void initialized(@Nullable Session session, @Nullable SessionError error);

        /**
         * Failure callback for authorize()
         * @param error
         */
        void didFailAuthorize(@NonNull SessionError error);

        /**
         * Failure callback for logout()
         * @param error
         */
        void didFailLogout(@NonNull SessionError error);

        /**
         * Callback whenever the session is lost.  Usually returned when token refreshing fails.
         * @param error
         */
        void didLoseSession(@NonNull SessionError error);

        /**
         * Currently not used.  Meant to be called when application is restarting.
         * @param session
         */
        void didResumeSession(Session session);

        /**
         * Callback when refresh session completed successfully.
         * @param session
         */
        void didRefreshSession(Session session);

        /**
         * Callback when network connectivity is lost
         */
        void didLoseNetwork();

        /**
         * Callback when network connectivity regained
         */
        void didGainNetwork();

        /**
         * Callback for events that have been registered for using registerForEvent()
         *    - this callback is received independently of the addSessionListener/
         *      removeSessionListener() calls.
         *    - use this for e.g. finishing an Activity when a login/logout succeeds
         */
        void notification(@NonNull NotificationReceiver.EventType event);

    }

    /**
     * Callback for getFreshSession() and refreshSession()
     * If successful session returned, otherwise SessionError
     */
    public interface TokenRefreshCallback {
        void tokenRefreshCallback(@Nullable Session session, @Nullable SessionError error);
    }
}
