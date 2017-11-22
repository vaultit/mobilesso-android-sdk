package org.vaultit.mobilesso.mobilessodemo;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.ColorRes;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.vaultit.mobilesso.mobilessosdk.IdentityProvider;
import org.vaultit.mobilesso.mobilessosdk.NotificationReceiver;
import org.vaultit.mobilesso.mobilessosdk.Session;
import org.vaultit.mobilesso.mobilessosdk.Session.SessionStatus;
import org.vaultit.mobilesso.mobilessosdk.SessionError;
import org.vaultit.mobilesso.mobilessosdk.SessionManager;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import javax.net.ssl.HttpsURLConnection;

public final class MainActivity extends AppCompatActivity
        implements SessionManager.SessionListener {

    // constant for logging
    private static final String TAG = "MainActivity";

    private static final AtomicLong NEXT_ID = new AtomicLong(0);
    private final long id = NEXT_ID.getAndIncrement();

    // constants for saving state on disk
    private static final String FILE_SAVED_APP_STATE = "app_savedState";
    private static final String KEY_REFRESH_TIMESTAMP = "refreshTimestamp";

    // constants for intents
    private static final String INTENT_EXTRA_AUTH_CALLBACK = "authorizationCallback";
    private static final String INTENT_EXTRA_LOGOUT_CALLBACK = "logoutCallback";

    // other constants
    private static final int READ_STREAM_BUFFER_SIZE = 1024;

    private SessionManager mSessionManager;
    private IdentityProvider mIdentityProvider;
    // contains person's card info, mostly display info
    private PersonInfo mPersonInfo;
    private ExecutorService mExecutor;

    private View mUserCardView = null;
    private TextView mSessionStatusLabel;

    private Context mContext = null;
    private Session mSession = null;

    private String getCachedProfileFileName() {
        return getFilesDir().getPath() + "/cached_profile.png";
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)  {

        super.onCreate(savedInstanceState);
        mExecutor = Executors.newSingleThreadExecutor();
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        Log.d(TAG, "onCreate(): getId=" + getId());
        mContext = this;
        mIdentityProvider = new IdentityProvider(
                getResources().getString(R.string.idp_discovery_uri),
                getResources().getString(R.string.idp_client_id),
                getResources().getString(R.string.idp_client_secret),
                getResources().getString(R.string.idp_auth_redirect_uri),
                getResources().getString(R.string.idp_logout_redirect_uri),
                getResources().getString(R.string.idp_scope_string));

        mSessionManager = new SessionManager(this,mIdentityProvider);
        mSession = mSessionManager.getSession();
        mPersonInfo = new PersonInfo();
        mUserCardView = findViewById(R.id.user_card);
        mSessionStatusLabel = (TextView) findViewById(R.id.session_status_label);

        refreshUi();
        // register for logout/login
        mSessionManager.registerForEvent(NotificationReceiver.EventType.LOGIN_COMPLETE);
        mSessionManager.registerForEvent(NotificationReceiver.EventType.LOGOUT_COMPLETE);
    }


    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // since we aren't using SINGLE_TOP
        Log.e(TAG, "onNewIntent(): getId=" + getId() + " this shouldn't show up!");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG,"onResume(): getId=" + getId());
        refreshUi();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG,"onPause(): getId=" + getId());
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG,"onStart(): getId=" + getId());

        if (mExecutor.isShutdown()) {
            mExecutor = Executors.newSingleThreadExecutor();
        }

        mSessionManager.addSessionListener(this);

        Intent intent = getIntent();

        if (intent.hasExtra(SessionManager.KEY_SESSION_ERROR_JSON)) {
            SessionError err;
            try {
                err = SessionError.jsonDeserialize(intent.getStringExtra(
                        SessionManager.KEY_SESSION_ERROR_JSON));
                Log.e(TAG,"onStart(): received intent with failure code= " +
                        SessionError.errorCode2string(err.getErrorCode()) +
                        " msg=" + err.getErrorMessage());
            } catch (JSONException ex) {
                Log.e(TAG, "onStart(): invalid SessionError json",ex);
            }

        } else if (intent.hasExtra(INTENT_EXTRA_AUTH_CALLBACK)) {
            Log.d(TAG,"onStart(): received authorization response with tokens");
            if (tokensReady() && mSession.isOnline()) {
                fetchPerson();
            }

        } else if (intent.hasExtra(INTENT_EXTRA_LOGOUT_CALLBACK)) {
            Log.d(TAG,"onStart(): received logout intent");

            mSessionManager.initialize();
        } else {
            Log.d(TAG, "onStart(): starting with initialize");

            mSessionManager.initialize();

        }


    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG,"onStop(): getId=" + getId());
        mSessionManager.removeSessionListener(this);
        mExecutor.shutdownNow();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy(): getId=" + getId());
        mSessionManager.unregisterAllEvents();
        mSessionManager.dispose();
        mSessionManager = null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Log.d(TAG, "onCreateOptionsMenu");
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        SharedPreferences appPrefs = getSharedPreferences(FILE_SAVED_APP_STATE, MODE_PRIVATE);
        switch (item.getItemId()) {
            case R.id.action_refresh:
                Log.d(TAG, "USER CHOICE: refresh data");
                if (mSessionManager.isInitialized()) {
                    SimpleDateFormat s = new SimpleDateFormat("dd.MM.yyyy hh:mm.ss", Locale.US);
                    String formattedDate = s.format(new Date());

                    if (!tokensReady()) {
                        performLogin();
                        return true;
                    }

                    appPrefs.edit()
                            .putString(KEY_REFRESH_TIMESTAMP, formattedDate)
                            .apply();
                    if (mSession.isOnline()) {
                        fetchPerson();
                    } else {
                        Log.d(TAG,"onOptionsItemSelected(): cannot fetch data b/c offline");
                    }
                } else {
                    Log.d(TAG, "onOptionsItemSelected(): need to start from scratch");
                    mSessionManager.initialize();
                }
                break;
            case R.id.action_logout:
                Log.d(TAG, "USER ACTION: logout");
                performLogout();
                break;

            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }


    // case where application has no information stored from previous runs
    private void performLogin() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(INTENT_EXTRA_AUTH_CALLBACK, true);

        Log.d(TAG, "performLogin(): initiating auth svc config retrieval ");
        HashMap<String, String> map = new HashMap<String, String>();
        map.put("acr_values","internal");
        mSessionManager.authorize(mContext,intent,map);
    }

    private void cleanLocalData() {
        Log.d(TAG,"cleanLocalData()");
        mPersonInfo = new PersonInfo();
        SharedPreferences appPrefs = getSharedPreferences(FILE_SAVED_APP_STATE, MODE_PRIVATE);
        appPrefs.edit()
                .clear()
                .apply();

    }

    @TargetApi(Build.VERSION_CODES.M)
    @SuppressWarnings("deprecation")
    private int getColorCompat(@ColorRes int color) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return getColor(color);
        } else {
            return getResources().getColor(color);
        }
    }

    // updates UI
    private void refreshUi() {
        View userCard = findViewById(R.id.user_card);

        if (mPersonInfo != null && mPersonInfo.getPhoto() != null) {
            ImageView photo = (ImageView) findViewById(R.id.photo);
            photo.setImageBitmap(mPersonInfo.getPhoto());

            TextView name = (TextView) findViewById(R.id.user_name);
            name.setText(mPersonInfo.getPersonName());

            TextView timestamp = (TextView) findViewById(R.id.timestamp);
            timestamp.setText(mPersonInfo.getFetchedTime());

            userCard.setVisibility(View.VISIBLE);
        } else {
            userCard.setVisibility(View.INVISIBLE);
        }

        if (mSession.getStatus() == SessionStatus.NO_SESSION) {
            mSessionStatusLabel.setText("NOT AUTHORIZED");
        }
        else if (mSession.isOnline()) {
            mSessionStatusLabel.setText("ONLINE");
        }
        else {
            mSessionStatusLabel.setText("OFFLINE");
        }
    }

    private void showModalDialog(String title, String text) {
        AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();
        alertDialog.setTitle(title);
        alertDialog.setMessage(text);
        alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
            new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });

        alertDialog.show();
    }

    @MainThread
    private void fetchPerson() {
        Log.d(TAG,"fetchPerson()");
        if (!tokensReady() ) {
            Log.e(TAG, "fetchPerson(): Cannot make request without tokensReady");
            return;
        }

        mSessionManager.getFreshSession(new SessionManager.TokenRefreshCallback() {
            @Override
            public void tokenRefreshCallback(
                    @Nullable Session session,
                    @Nullable SessionError error) {
                if (error != null) {
                    Log.d(TAG, "fetchPerson failed err=" + error.getErrorMessage());

                    Bitmap profilePhoto = BitmapFactory.decodeFile(getCachedProfileFileName());
                    mPersonInfo.setPhoto(profilePhoto);
                    refreshUi();
                } else {
                    mExecutor.execute(new Runnable() {
                        public void run() {
                            readPerson();
                        }
                    });
                }
            }
        });
    }

    @WorkerThread
    private void readPerson() {
        String accessToken = mSession.getAccessToken();

        Log.d(TAG,"readPerson() accessToken=" + accessToken);
        URL personEndpoint;

        try {
            personEndpoint = new URL(getResources().getString(R.string.backend_person_get));
        } catch (MalformedURLException urlEx) {
            Log.e(TAG, "readPerson() Failed to construct person info endpoint URL", urlEx);
            return;
        }

        InputStream personResponse = null;
        try {
            HttpsURLConnection conn = (HttpsURLConnection) personEndpoint.openConnection();
            conn.setRequestProperty("Authorization", "Bearer " + accessToken);
            conn.setInstanceFollowRedirects(false);
            personResponse = conn.getInputStream();
            String response = readStream(personResponse);
            updatePerson(new JSONObject(response),null);
        } catch (IOException ioEx) {
            Log.e(TAG, "readPerson() Network error when querying card info endpoint (invalid access token?): ", ioEx);
            updatePerson(null, "Network error when querying card info endpoint: "+ ioEx.getMessage());
        } catch (JSONException jsonEx) {
            Log.e(TAG, "readPerson() Failed to parse card info response");
            updatePerson(null, "Failed to parse card info response");
        } finally {
            if (personResponse != null) {
                try {
                    personResponse.close();
                } catch (Exception Ex) {}
            }
        }
    }

    @WorkerThread
    private void updatePerson(final JSONObject personJSON, final String error) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            public void run() {
                if (error != null) {
                    Toast.makeText(mContext, error, Toast.LENGTH_SHORT).show();
                    return;
                }
                try {
                    Log.d(TAG, "updatePerson() response=" + personJSON.toString());
                    JSONArray jArr = personJSON.getJSONArray("names");
                    mPersonInfo.setPersonName(jArr.getJSONObject(0).getString("full_name"));
                    // Set timestamp for fetched data
                    SimpleDateFormat s = new SimpleDateFormat("dd.MM.yyyy hh:mm.ss", Locale.US);
                    String formattedDate = s.format(new Date());
                    mPersonInfo.setFetchedTime(formattedDate);
                    Log.d(TAG, "updatePerson(): name=" + mPersonInfo.getPersonName());
                    refreshUi();
                    fetchPhoto();

                } catch (JSONException ex) {
                    Log.e(TAG, "updatePerson() Failed to read person info JSON", ex);
                }
            }
        });
    }

    @MainThread
    private void fetchPhoto() {
        Log.d(TAG,"fetchPhoto()");
        if (!tokensReady()) {
            Log.e(TAG, "fetchPhoto(): Cannot make fetchCard request without tokensReady");
            return;
        }

        mSessionManager.getFreshSession(new SessionManager.TokenRefreshCallback() {
            @Override
            public void tokenRefreshCallback(
                    @Nullable Session session,
                    @Nullable SessionError error) {
                if (error != null) {
                    Log.e(TAG, "fetchPhoto failed err=" + error.getErrorMessage());
                } else {
                    Log.d(TAG, "fetchPhoto succeeded");
                    mExecutor.execute(new Runnable() {
                        public void run() {
                            readPhoto();
                        }
                    });
                }
            }
        });
    }

    private boolean tokensReady() {
        return mSession.getStatus() == SessionStatus.VALID;
    }

    @WorkerThread
    private void readPhoto() {
        if (mSession.getStatus() == SessionStatus.NO_SESSION) {
            Log.e(TAG,"readPhoto(): session doesn't exist!");
            return;
        }
        String accessToken = mSession.getAccessToken();

        Log.d(TAG,"readPhoto()");
        URL photoEndpoint;

        try {
            photoEndpoint = new URL(getResources().getString(R.string.backend_photo_get));
            Log.d(TAG,"url=" + photoEndpoint);
        } catch (MalformedURLException urlEx) {
            Log.e(TAG, "readPhoto() Failed to construct photo endpoint URL", urlEx);
            return;
        }

        Bitmap photo;
        InputStream photoStream = null;
        try {
            HttpsURLConnection conn = (HttpsURLConnection) photoEndpoint.openConnection();
            conn.setRequestProperty("Authorization", "Bearer " + accessToken);
            conn.setInstanceFollowRedirects(false);
            photoStream = conn.getInputStream();
            photo = BitmapFactory.decodeStream(photoStream);

            FileOutputStream imageFile = new FileOutputStream(getCachedProfileFileName());
            photo.compress(Bitmap.CompressFormat.PNG, 100, imageFile);

            imageFile.close();
            photoStream.close();

            updatePhoto(photo,null);
        } catch (IOException ioEx) {
            Log.e(TAG, "readPhoto() Network error when querying for photo (invalid access token?)", ioEx);
            updatePhoto(null,"Network error when querying for photo "+ ioEx.getMessage());
        }  finally {
            if (photoStream != null) {
                try {
                    photoStream.close();
                } catch (IOException ioEx) {
                    Log.e(TAG, "readPhoto() Failed to close photo response stream", ioEx);
                }
            }
        }
    }
    @WorkerThread
    private void updatePhoto(final Bitmap photo, final String error) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            public void run() {
                if (error != null) {
                    Toast.makeText(mContext, error, Toast.LENGTH_SHORT).show();
                    return;
                }
                try {
                    mPersonInfo.setPhoto(photo);
                    Log.d(TAG, "updatePhoto() photo downloaded ok");
                    refreshUi();
                } catch (Exception ex) {
                    Log.e(TAG, "updatePhoto() Failed to show photo", ex);
                }
            }
        });
    }

    private static String readStream(InputStream stream) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(stream));
        char[] buffer = new char[READ_STREAM_BUFFER_SIZE];
        StringBuilder sb = new StringBuilder();
        int readCount;
        while ((readCount = br.read(buffer)) != -1) {
            sb.append(buffer, 0, readCount);
        }
        return sb.toString();
    }

    // intent for starting up main activity after logout response redirect
    private Intent createPostLogoutIntent() {
        Intent intent = new Intent(mContext, MainActivity.class);
        intent.putExtra(INTENT_EXTRA_LOGOUT_CALLBACK, true);
        return intent;
    }

    /**
     * performs logout request if token refresh succeeds.
     * NOTE: not completely reliable, since token refresh can also fail (besides the case where
     * someone else has done a logout) when refresh token is too old (> 4h)
     * SIDE-EFFECTS:  cleans session data
     */
    private void performLogout() {
        Log.d(TAG,"performLogout()");

        mSessionManager.logout(
                createPostLogoutIntent());

        cleanLocalData();
        refreshUi();
    }

    private long getId() {
        return id;
    }

    //
    // SessionListener interface callbacks
    //

    @MainThread
    public void initialized(@Nullable Session session, @Nullable SessionError error) {
        if (session != null) {
            Log.d(TAG,"initialized() completed successfully id=" + getId());
            if (session.isOnline()) {
                // no need for refresh, since initialize has already done that
                fetchPerson();
            }
            refreshUi();
        } else if (error != null) {
            Log.e(TAG,"initialized(): failed to initialize session id=" + getId()
                    +" error=" + error.getErrorMessage());
        } else {
            Log.e(TAG,"internal error: neither parameter available");
        }
    }

    @Override
    public void didFailAuthorize(@NonNull SessionError error) {
        Log.e(TAG,"didFailAuthorize() id=" + getId() +" error=" + error.getErrorMessage());
    }

    @Override
    public void didFailLogout(@NonNull SessionError error) {
        Log.e(TAG,"didFailLogout() id=" + getId() +" error=" + error.getErrorMessage());
    }

    @MainThread
    public void didLoseSession(@NonNull SessionError error) {
        Log.d(TAG,"didLoseSession() id=" + getId() +" error=" + error.getErrorMessage());
    }
    @MainThread
    public void didResumeSession(Session session) {
        Log.d(TAG,"didResumeSession() id=" + getId());
    }

    @MainThread
    public void didRefreshSession(Session session) {
        Log.d(TAG,"didRefreshSession() id=" + getId());
    }

    @MainThread
    public void didLoseNetwork() {
        Log.d(TAG,"didLoseNetwork() id=" + getId());
        refreshUi();
    }

    @MainThread
    public void didGainNetwork() {
        Log.d(TAG,"didGainNetwork() id=" + getId());
        refreshUi();
    }
    @MainThread
    public void notification(@NonNull NotificationReceiver.EventType event) {
        Log.d(TAG,"notification(): received=" + event);
    }
}
