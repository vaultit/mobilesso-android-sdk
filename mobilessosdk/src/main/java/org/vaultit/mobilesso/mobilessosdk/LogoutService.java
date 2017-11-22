package org.vaultit.mobilesso.mobilessosdk;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;
import android.support.customtabs.CustomTabsIntent;
import android.text.TextUtils;
import android.util.Log;

import java.io.IOException;
import java.net.URL;


/**
 * New class for implementing logout functionality, which is missing from AppAuth library
 */
class LogoutService {
    // logging
    private static final String TAG = "LogoutService";

    // HTTP query parameters for logout
    private static final String PARAM_REDIRECT_URI = "post_logout_redirect_uri";
    private static final String PARAM_ID_TOKEN= "id_token_hint";


    @VisibleForTesting
    Context mContext;

    @NonNull
    private final UrlBuilder mUrlBuilder;

    @NonNull
    private final BrowserHandler mBrowserHandler;

    private boolean mDisposed = false;
    private Data mData = null;

    /**
     * Creates an LogoutService instance based on the provided configuration. Note that
     * instances of this class must be manually disposed when no longer required, to avoid
     * leaks (see {@link #dispose()}.
     */
    public LogoutService(@NonNull Context context) {
        this(context,
                DefaultUrlBuilder.INSTANCE,
                new BrowserHandler(context));
    }

    /**
     * Constructor that injects a url builder into the service for testing.
     */
    @VisibleForTesting
    protected LogoutService(@NonNull Context context,
                            @NonNull UrlBuilder urlBuilder,
                            @NonNull BrowserHandler browserHandler) {
        Log.d(TAG,"constructor");
        mContext = Preconditions.checkNotNull(context);
        mUrlBuilder = Preconditions.checkNotNull(urlBuilder);
        mBrowserHandler = Preconditions.checkNotNull(browserHandler);
        mData = Data.getInstance(context.getApplicationContext());
    }


    public void performLogoutRequest(
            @NonNull String idToken,
            @NonNull IdentityProvider identityProvider,
            @NonNull Uri logoutEndpoint,
            @NonNull Intent postLogoutCallbackIntent,
            @NonNull CustomTabsIntent customTabsIntent) {

        Uri.Builder uriBuilder = logoutEndpoint.buildUpon()
                .appendQueryParameter(PARAM_REDIRECT_URI,
                        identityProvider.getLogoutRedirectUri().toString())
                .appendQueryParameter(PARAM_ID_TOKEN, idToken);
        Uri logoutUri = uriBuilder.build();
        Log.d(TAG,"performLogoutRequest(): logoutUri=" + logoutUri.toString());

        mData.sessionReset();  // removes only tokens, full reset done later
        mData.setLogoutResponseIntent(postLogoutCallbackIntent);
        mData.saveData();


        Intent newCustomTabsIntent = customTabsIntent.intent;
        newCustomTabsIntent.setData(logoutUri);
        if (TextUtils.isEmpty(newCustomTabsIntent.getPackage())) {
            newCustomTabsIntent.setPackage(mBrowserHandler.getBrowserPackage());
        }

        Log.d(TAG,"Using " + newCustomTabsIntent.getPackage() + " as browser for logout");
        newCustomTabsIntent.putExtra(CustomTabsIntent.EXTRA_TITLE_VISIBILITY_STATE, CustomTabsIntent.NO_TITLE);
        newCustomTabsIntent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);

        Log.d(TAG,"Initiating logout request to " +
                logoutEndpoint);
        mContext.startActivity(newCustomTabsIntent);
    }



    /**
     * Disposes state that will not normally be handled by garbage collection. This should be
     * called when the logout service is no longer required, including when any owning
     * activity is paused or destroyed (i.e. in {@link android.app.Activity#onStop()}).
     */
    public void dispose() {
        if (mDisposed) {
            return;
        }
        mBrowserHandler.unbind();
        mDisposed = true;
    }

    private void checkNotDisposed() {
        if (mDisposed) {
            throw new IllegalStateException("Service has been disposed and rendered inoperable");
        }
    }


    @VisibleForTesting
    interface UrlBuilder {
        URL buildUrlFromString(String uri) throws IOException;
    }

    static class DefaultUrlBuilder implements UrlBuilder {
        public static final DefaultUrlBuilder INSTANCE = new DefaultUrlBuilder();

        DefaultUrlBuilder() {}

        public URL buildUrlFromString(String uri) throws IOException {
            return new URL(uri);
        }
    }
}
