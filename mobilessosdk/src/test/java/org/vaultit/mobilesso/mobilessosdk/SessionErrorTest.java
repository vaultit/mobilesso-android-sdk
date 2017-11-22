package org.vaultit.mobilesso.mobilessosdk;


import static net.openid.appauth.AuthorizationException.TYPE_GENERAL_ERROR;
import static net.openid.appauth.AuthorizationException.TYPE_OAUTH_AUTHORIZATION_ERROR;
import static net.openid.appauth.AuthorizationException.TYPE_OAUTH_TOKEN_ERROR;
import static org.junit.Assert.assertNotEquals;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.vaultit.mobilesso.mobilessosdk.SessionError.ErrorCode.*;

import net.openid.appauth.AuthorizationException;

@RunWith(RobolectricTestRunner.class)
@Config(manifest=Config.NONE)
public class SessionErrorTest {
    private static final String FAKE_STRING = "HELLO WORLD";

    @Test
    public void serializeDeserializeSimpleError() throws Exception {
        SessionError se = new SessionError(SessionError.ErrorCode.INIT_AUTH_STATE_PARSE_ERROR,
                "parse state error");
        String serialized = se.jsonSerializeString();
        SessionError se_deserialized = SessionError.jsonDeserialize(serialized);
        assertEquals(se, se_deserialized);
    }

    @Test
    public void serializeDeserializeComplexError() throws Exception {
        AuthorizationException ex = new AuthorizationException(5,5,"error5",
                "this is error5",null,null);
        SessionError se = new SessionError(SessionError.ErrorCode.INIT_AUTH_STATE_PARSE_ERROR,
                "parse state error",ex);
        String serialized = se.jsonSerializeString();
        SessionError se_deserialized = SessionError.jsonDeserialize(serialized);
        assertEquals(se, se_deserialized);
    }
    @Test
    public void testSerializeNegativeCase() throws Exception {
        AuthorizationException ex = new AuthorizationException(5,7,"error5",
                "this is error5",null,null);
        SessionError se = new SessionError(SessionError.ErrorCode.INIT_AUTH_STATE_PARSE_ERROR,
                "parse state error",ex);
        String serialized = se.jsonSerializeString();
        String serialized2 = serialized.replace(":5", ":6");
        SessionError se_deserialized = SessionError.jsonDeserialize(serialized2);
        assertNotEquals(se, se_deserialized);
    }
    @Test
    public void testEqualsForSimpleError() throws Exception {
        SessionError se1 = new SessionError(SessionError.ErrorCode.INIT_AUTH_STATE_PARSE_ERROR,
                "parse state error");
        SessionError se2 = new SessionError(SessionError.ErrorCode.INIT_AUTH_STATE_PARSE_ERROR,
                "parse state error");
        assertEquals(se1,se2);
    }
    @Test
    public void testNotEqualsForSimpleError() throws Exception {
        SessionError se1 = new SessionError(SessionError.ErrorCode.INIT_AUTH_STATE_PARSE_ERROR,
                "parse state error");
        SessionError se2 = new SessionError(SessionError.ErrorCode.SESSION_REFRESH_NETWORK_ERROR,
                "parse state error");
        assertNotEquals(se1,se2);
        SessionError se3 = new SessionError(SessionError.ErrorCode.INIT_AUTH_STATE_PARSE_ERROR,
                "parse state error here too");
        assertNotEquals(se1,se3);
    }
    @Test
    public void testGetErrorBasedOnException() {
        // refresh server error based on code
        assertEquals(SESSION_REFRESH_SERVER_ERROR,SessionError.getErrorCode(
                new AuthorizationException(TYPE_OAUTH_AUTHORIZATION_ERROR,
                    AuthorizationException.AuthorizationRequestErrors.SERVER_ERROR.code,
                    null,null,null,null),"refresh"));
        // auth server error based on code
        assertEquals(AUTHORIZATION_SERVER_ERROR,SessionError.getErrorCode(
                new AuthorizationException(TYPE_OAUTH_AUTHORIZATION_ERROR,
                    AuthorizationException.AuthorizationRequestErrors.TEMPORARILY_UNAVAILABLE.code,
                    null,null,null,null),"authorization"));
        // auth server error based on code
        assertEquals(AUTHORIZATION_SERVER_ERROR,SessionError.getErrorCode(
                new AuthorizationException(TYPE_OAUTH_AUTHORIZATION_ERROR,
                        AuthorizationException.GeneralErrors.SERVER_ERROR.code,
                        null,null,null,null),"authorization"));
        // refresh network error based on code
        assertEquals(SESSION_REFRESH_NETWORK_ERROR,SessionError.getErrorCode(
                new AuthorizationException(TYPE_OAUTH_AUTHORIZATION_ERROR,
                    AuthorizationException.GeneralErrors.NETWORK_ERROR.code,
                    null,null,null,null),"refresh"));
        // auth network error based on code
        assertEquals(AUTHORIZATION_NETWORK_ERROR,SessionError.getErrorCode(
                new AuthorizationException(TYPE_OAUTH_AUTHORIZATION_ERROR,
                    AuthorizationException.GeneralErrors.NETWORK_ERROR.code,
                    null,null,null,null),"authorization"));
        // refresh oath error based on type
        assertEquals(SESSION_REFRESH_OAUTH_ERROR,SessionError.getErrorCode(
                new AuthorizationException(TYPE_OAUTH_AUTHORIZATION_ERROR,
                    AuthorizationException.AuthorizationRequestErrors.ACCESS_DENIED.code,
                    null,null,null,null),"refresh"));
        // auth oath error based on type
        assertEquals(AUTHORIZATION_OATH_ERROR,SessionError.getErrorCode(
                new AuthorizationException(TYPE_OAUTH_TOKEN_ERROR,
                    AuthorizationException.AuthorizationRequestErrors.ACCESS_DENIED.code,
                    null,null,null,null),"authorization"));
        // unknown error
        assertEquals(UNKNOWN_ERROR,SessionError.getErrorCode(
                new AuthorizationException(TYPE_GENERAL_ERROR,
                        AuthorizationException.AuthorizationRequestErrors.ACCESS_DENIED.code,
                        null,null,null,null),"authorization"));
    }
}