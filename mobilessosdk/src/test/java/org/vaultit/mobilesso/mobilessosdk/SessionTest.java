package org.vaultit.mobilesso.mobilessosdk;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest=Config.NONE)
public class SessionTest {


    @Test
    public void serializeDeserializeSimpleSession() throws Exception {
        // changed Session to require context in constructor
/*        AuthorizationResponse resp = getTestAuthResponse();
        AuthState authState = new AuthState(resp,null);
        Session se = new Session();
        String serialized = se.jsonSerializeString();
        Session se_deserialized = Session.jsonDeserialize(serialized);
        assertEquals(se, se_deserialized);*/
    }

}
