package org.vaultit.mobilesso.mobilessosdk;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test for testing clean data.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class, sdk=16)
public class InitUnitTest {

    @Mock
    Context mockContext;
    @Mock
    IdentityProvider mockIdentityProvider;
    @Mock
    SharedPreferences mockPrefs;
    @Mock
    PackageManager mockManager;
    @Mock
    SharedPreferences.Editor mockEditor;
    @Mock
    SharedPreferences.Editor mockEditor2;

    @Test
    public void clean_data_modifies_sharedpreferences() throws Exception {
        MockitoAnnotations.initMocks(this);

        //when(mockManager.queryIntentActivities(net.openid.appauth.browser.BrowserSelector.BROWSER_)).thenReturn(64);
        when(mockContext.getPackageManager()).thenReturn(mockManager);
        when(mockEditor2.commit()).thenReturn(true);
        when(mockEditor.clear()).thenReturn(mockEditor2);
        when(mockPrefs.edit()).thenReturn(mockEditor);
        when(mockContext.getSharedPreferences("mobileSsoSdk_savedState", mockContext.MODE_PRIVATE)).
                thenReturn(mockPrefs);
        when(mockIdentityProvider.serializeToJson()).thenReturn("");

        SessionManager sdk = new SessionManager(mockContext, mockIdentityProvider);
        //sdk.cleanLocalData();
        verify(mockPrefs).edit();
/*        verify(mockEditor).clear();
        verify(mockEditor2).apply();*/

        /*
        IdentityProvider provider = new IdentityProvider(
                "Name", false,"http://localhost:8080/oxauth/.well-known/openid-configuration",
                "", // auth endpoint is discovered
                "", // token endpoint is discovered
                "", // dynamic registration not supported
                "", // logout endpoint is discovered
                "client_id",
                "client_secret",
                "test.mobilessodemo.auth://oidc_callback",
                "test.mobilessodemo.logout://oidc_callback",
                "opendid test",
                "Testi",
                android.R.color.white);
        assertEquals("client_id", provider.getClientId());
        */

    }
}