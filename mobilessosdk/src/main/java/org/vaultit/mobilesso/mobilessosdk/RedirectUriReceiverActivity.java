/*
 * Copyright 2015 The AppAuth for Android Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vaultit.mobilesso.mobilessosdk;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import java.util.Map;

/**
 * Activity that receives the redirect Uri sent by the OpenID endpoint. This activity gets launched
 * when the user approves the app for use and it starts the {@link PendingIntent} given in
 * {@link AuthorizationService#performAuthorizationRequest}.
 *
 * <p>App developers using this library <em>must</em> to register this activity in the manifest
 * with one intent filter for each redirect URI they are intending to use.
 *
 * <pre>
 * {@code
 * < intent-filter>
 *   < action android:name="android.intent.action.VIEW"/>
 *   < category android:name="android.intent.category.DEFAULT"/>
 *   < category android:name="android.intent.category.BROWSABLE"/>
 *   < data android:scheme="REDIRECT_URI_SCHEME"/>
 * < /intent-filter>
 * }
 * </pre>
 */
@SuppressLint("Registered")
public class RedirectUriReceiverActivity extends AppCompatActivity {
    private static final String TAG="LogoutRedirect";

    @Override
    public void onCreate(Bundle savedInstanceBundle) {
        super.onCreate(savedInstanceBundle);
        Uri uri = getIntent().getData();
        Log.d(TAG,"onCreate(): data=" + uri.toString());

        Data data = Data.getInstance(this.getApplicationContext());
        Intent logoutResponse = data.getLogoutResponseIntent();

        // send any logout notifications that have been registered for
        Map<Long,NotificationReceiver> receivers = data.getNotificationReceivers();
        for (Map.Entry<Long, NotificationReceiver> entry : receivers.entrySet()) {
            entry.getValue().sendEvent(NotificationReceiver.EventType.LOGOUT_COMPLETE);
        }

        // delete all data from disk & most data from memory
        data.dataReset(this);

        startActivity(logoutResponse);


        finish();
    }
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG,"onDestroy()");
    }
}
