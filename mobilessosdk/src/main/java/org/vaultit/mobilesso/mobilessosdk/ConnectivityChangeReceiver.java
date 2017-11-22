package org.vaultit.mobilesso.mobilessosdk;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/**
 *
 */
public class ConnectivityChangeReceiver extends BroadcastReceiver {
    private static final String TAG = "ConnectivityChangeRcvr";
    private boolean mOfflineMode = false;
    private Context mActivityContext = null;
    private Data mData = null;

    public ConnectivityChangeReceiver(Context context, Data data) {
        mData = data;
        mActivityContext = context;
        mOfflineMode = !mData.getNetworkAvailable();
        Log.d(TAG,"constructor:  mOfflineMode=" + mOfflineMode);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        boolean oldOfflineMode = mOfflineMode;
        mOfflineMode = !isConnected(context);
        Log.d(TAG,"onReceive() mOfflineMode=" + mOfflineMode + " oldOfflineMode="  + oldOfflineMode);

        mData.setNetworkAvailable(!mOfflineMode);
        // notify specific listener about connectivity event
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            public void run() {
                Log.d(TAG, "onReceive(): calling listener " +
                        (mOfflineMode ? " didLoseNetwork" : " didGainNetwork"));
                mData.setNetworkAvailable(!mOfflineMode);

                if (mOfflineMode) {
                    ((SessionManager.SessionListener)mActivityContext).didLoseNetwork();
                } else {
                    ((SessionManager.SessionListener)mActivityContext).didGainNetwork();
                }
            }
        });
    }

    private boolean isConnected(Context context) {
        ConnectivityManager conn =  (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = conn.getActiveNetworkInfo();

        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    // when registering/unregistering IntentReceiver, must use Activity context of calling Activity
    Context getActivityContext() {
        return mActivityContext;
    }
}
