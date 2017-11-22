package org.vaultit.mobilesso.mobilessosdk;


import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.util.EnumSet;

public class NotificationReceiver {
    private static final String TAG = "NotificationReceiver";
    private WeakReference<Context> mWeakContextRef = null;
    private EnumSet<EventType> events = null;

    /**
     * Event type to receive when calling SessionManager.registerForEvent()
     */
    public enum EventType {
        LOGIN_COMPLETE,
        LOGOUT_COMPLETE
    }

    NotificationReceiver(@NonNull Context context, @NonNull EnumSet<EventType> events) {
        mWeakContextRef = new WeakReference<Context>(context);
        this.events = events;
        Log.d(TAG,"constructor()");
    }

    EnumSet<EventType> getEvents() {
        return events;
    }

    void setEvents(EnumSet<EventType> events) {
        this.events = events;
    }

    Context getContext() {
        return mWeakContextRef.get();
    }

    void sendEvent(EventType event) {
        Context context = mWeakContextRef.get();
        if (events.contains(event) && context != null) {
            Log.d(TAG,"sendEvent(): sending event=" + event);
            ((SessionManager.SessionListener)context).notification(event);
        }
    }
}
