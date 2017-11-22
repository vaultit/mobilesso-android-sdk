package org.vaultit.mobilesso.mobilessodemo;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;

class ConnectionUtilities {
    private static final String TAG = "ConnectionUtilities";

    public static boolean isNetworkAvailable(Context context) {
        Log.d(TAG, "isNetworkAvailable()");
        ConnectivityManager connectivityManager
                = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }
    // perform DNS lookup with timeout (in ms);
    // returns null if error or timeout occurred
    public static URL dnsLookup (URL url, int timeout) {
        URL newUrl = null;
        Log.d(TAG, "dnsLookup()");
        // Resolve the host IP on a new thread
        DNSResolver dnsResolver = new DNSResolver(url.getHost());
        Thread t = new Thread(dnsResolver);
        t.start();

        try {
            t.join(timeout);   // give child thread time to perform DNS query
        } catch (InterruptedException e) {
            Log.d(TAG, "DNS lookup interrupted");
            return null;
        }

        InetAddress inetAddr = dnsResolver.get();
        if(inetAddr==null) {
            Log.d(TAG, "DNS timed out for url=" + url.toString());
            return null;
        }

        try {
            newUrl = new URL(url.getProtocol(),inetAddr.getHostAddress(),url.getPort(),url.getFile());
        } catch (MalformedURLException e) {
            Log.e(TAG, "Internal error", e);
        }

        return newUrl;
    }

    private static class DNSResolver implements Runnable {
        private final String domain;
        private InetAddress inetAddr;

        public DNSResolver(String domain) {
            this.domain = domain;
        }

        public void run() {
            try {
                InetAddress addr = InetAddress.getByName(domain);
                set(addr);
            } catch (UnknownHostException e) {
            }
        }

        public synchronized void set(InetAddress inetAddr) {
            this.inetAddr = inetAddr;
        }
        public synchronized InetAddress get() {
            return inetAddr;
        }
    }
}