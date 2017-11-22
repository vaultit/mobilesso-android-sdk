# VaultITMobileSSOFramework for Android

## Configuration
First part of the configuration is the adding the
library to the Android-project. Then configuring
the OpenID Client settings and the OpenID Connect Provider Discovery
URL, some of the client information you'll receive when you
register your application.

### Installation
Create new Android-project. Add flatDirs-configuration to the repositories-section of the
top-level build.gradle-file:

    allprojects {
        repositories {
            jcenter()
            flatDir {
                dirs 'libs'
            }
        }
    }

Compile the mobilessosdk library by typing (or alternatively use Android Studio to build):

    ./gradlew assembleRelease

Copy the resulting *vaultit-mobilessosdk-X.Y.Z-release.aar*
from the distribution package (found in *./mobilessosdk/build/outputs/aar/*) to *./app/libs* directory. 
Then add it and the
[AppAuth](https://github.com/openid/AppAuth-Android "AppAuth Android library")
library as a dependency to the application build.gradle-file
( app/build.gradle ):

    implementation(name:'vaultit-mobilessosdk-X.Y.Z-release', ext:'aar')
    implementation 'net.openid:appauth:0.7.0'

Then do gradle sync (if using Android Studio).

### OpenID Connect Provider settings

The library has a *IdentityProvider* class, which is used to encapsulate
OpenID Connect provider settings. Client id and client secret are
issued when registering your application and OpenID Connect
Discovery URL is the providers discovery URL for the environment.
The *Getting started with VaultITMobileSSOFramework* document
has the discovery URL endpoints for different environments.

Below is the information that is needed to construct *IdentityProvider*.
Most of the information the application provider receives upon registering
its application for Nordic eID.

* discoveryEndpoint (environment's OpenID Connect Provider Discovery URL), e.g.
        "https://nordic-eid-gluu.qvarnlabs.net/.well-known/openid-configuration"
* clientId (app client id, generated when registering an application for Nordic eID), e.g.
        "@!2027.831B.4505.5985!0001!200B.B5FE!0008!74EE.DE66"
* clientSecret (app client secret, generated when registering an application for Nordic eID), e.g.
        "epbqEmD3AkROCdPsxBSe"
* redirectUri (app redirect URL after login), e.g.
         "com.example.virtualcard.auth://oidc_callback"
* logoutRedirectUri (app redirect URL after logout), e.g.
         "com.example.virtualcard.logout://oidc_callback"
* scope (OpenID scopes for application. See the
*Getting started with VaultITMobileSSOFramework* document for the list
of available scopes), e.g. 
         "openid profile email vcbe_virtual_cards_post vcbe_virtual_cards_delete gluu_fido_u2f_revoke"



## Basic VaultITMobileSSOFramework usage
Below are instructions how to use the VaultITMobileSSOFramework
in Android application. Implement *SessionManager.SessionListener*
in your activity class and construct the *SessionManager* class.
The *IdentityProvider* class is used to pass on the
the OpenID Connect Provider and client settings to the Session Manager.


### Initialization
The SessionManager needs to be created when the activity starts up. 
The activity context and IdentityProvider objects are given as parameters.
Then initialize() must be called.  Initialization will fetch information from the OpenID Connect
provider discovery endpoint and load previously stored session data.


        @Override
        protected void onCreate(Bundle savedInstanceState)  {
            ....
            mSessionManager  = new SessionManager(this,mIdentityProvider);
            if (!mSessionManager.isInitialized()) {
                mSessionManager.initialize();
            }
            ....
        }
        
Here is a sample SessionListener.initialized() callback:

    public void initialized(@Nullable Session session, @Nullable SessionError error) {
        if (session != null) {   // ie. refresh token is valid
            if (session.isOnline()) {  
                // no need for refresh, since initialize has already done that
                fetchPerson();
            } else {
                // offline
                ...
            }
        } else if (error != null) {
            Log.e(TAG,"initialized(): failed to initialize session error=" + 
                error.getErrorMessage());
        }
    }
        
### SessionListener
The framework comes with *SessionListener* interface
(org.vaultit.mobilesso.mobilessosdk.SessionManager.SessionListener).
Implement the interface in the application to receive notifications
and changes to session status.

To initialize listening to events, place the *addSessionListener()* call to *onStart()*:

    protected void onStart() {
        ...
        mSessionManager.addSessionListener(mContext);
        ....
    }
    
To terminate listening to events, place the *removeSessionListener()* call to *onStop()*:

    protected void onStop() {
        ...
        mSessionManager.removeSessionListener(this);
        ...
    }

The *SessionListener.notification()* callback is different, it will be activated by calling 
*registerForEvent()* and unregistered by calling *unregisterAllEvents()*.    The idea behind 
this is to receive events outside of onStart()/onStop() time frame.  This is useful e.g. for 
finishing an Activity after a logout or login.
    
        
### <a name="executor"></a>Executor
Use e.g. [Executors](https://developer.android.com/reference/java/util/concurrent/Executors.html)
to launch jobs in separate threads. Quick example:

        private ExecutorService mExecutor;
        ....
        if (mExecutor.isShutdown()) {
          mExecutor = Executors.newSingleThreadExecutor();
        }
        ...
        mSessionManager.getFreshSession(new SessionManager.TokenRefreshCallback() {
            @Override
            public void tokenRefreshCallback(
                    @Nullable Session session,
                    @Nullable SessionError error) {
                if (error != null) {
                    Log.e(TAG, "failed to refresh token, err=" + error.getErrorMessage());
                } else {
                    Log.d(TAG, "token fetch succeeded");
                    mExecutor.execute(new Runnable() {
                        public void run() {
                            readPhoto();  // this function performs network access
                        }
                    });
                }
            }
        });
            
SSO (single sign-on) library calls do not need to be called in a separate worker thread, although they may spawn 
threads themselves when running.  All SSO library callbacks will be called in the main/UI thread.  
In the above example, the SSO callback tokenRefreshCallback() is called in the main/UI thread context,
and thus the networking code in readPhoto() requires that mExecutor is used to run it in a separate
worker thread.


### Starting authentication process

The Nordic eID supports multiple different authentication method.
Currently they are bankid (for Swedish users), tupas (for
Finnish users) and internal (general username/password).
You can define the used ACR by giving *Map<String,String> additionalParams*
as a parameter to the authorize-function. The map should have a key *acr_values*
with one of the following values:

* "internal"  (default)
* "tupas"
* "bankid"

Authentication process will redirect the user to the OpenID Connect
Providers authentication process. It will use the system browser to
achieve SSO (single sign-on) between different applications. After successful
authentication and token exchange, the VaultITMobileSSOFramework
will redirect to the given application's Intent (given as parameter). Application can request the
access token from VaultITMobileSSOFramework and perform authenticated
request to protected APIs. Use the method:

    public void authorize(Context context, Intent tokenResponseIntent, Map<String,String> additionalParams)

to start the authentication process.  Here is an example of a call:

        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(INTENT_EXTRA_AUTH_CALLBACK, true);
        HashMap<String, String> map = new HashMap<String, String>();
        map.put("acr_values","internal");
        mSessionManager.authorize(mContext, intent, map);
           
to receive the callback intent in MainActivity:

    void onCreate() {
        ...
        if (intent.hasExtra(INTENT_EXTRA_AUTH_CALLBACK)) {
            if (!intent.hasExtra(SessionManager.KEY_SESSION_ERROR_JSON) {
                session = mSessionManager.getSession()
                if (session.getStatus() == SessionStatus.VALID && session.isOnline()) {
                   // successful authentication, now fetch some data...
                }
            } else {
                // handle error
            }
        }
    }
            

### Logout
Logout method will remove the user's login session.   It will first refresh the access token,
in an attempt to ensure the success of the logout operation.   
The actual logout will be performed with the
system browser and it is redirected back to the application using the
Intent that was given as a parameter (similar to the *authorize()* call). Call

    public void logout(final Intent intent)

to start the logout process.  After calling *logout()*, the application needs to 
call *initialize()*, since all session data will be deleted.

### Refresh tokens
The access token's expiration time is relatively short (currently e.g. 4h). After it has
expired, a fresh access token can be requested by the calling method:

    public void getFreshSession(TokenRefreshCallback callback)

This will renew access token and ID token from OpenID Connect Provider.  See the example 
in section [Executor](#executor)

### Authentication status
By implementing the *SessionListener* and registering the listener,
the application will receive changes to the session status.  In addition to this, the 
application can get an instance of the session by calling

    Session session = mSessionManager.getSession()
    
to obtain authentication session state information.

    if (session.getStatus() == SessionStatus.VALID) {
        ...
    }
    
or to retrieve the access token:

    String accessToken = session.getAccessToken();
    
The session object is "live", ie. its state will be updated as the session state changes.

## Known issues


## License
The VaultITMobileSSOFramework is released under the
[Apache License Version 2.0](http://www.apache.org/licenses/) license.
