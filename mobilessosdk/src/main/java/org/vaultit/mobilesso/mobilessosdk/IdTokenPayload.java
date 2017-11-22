package org.vaultit.mobilesso.mobilessosdk;


import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Base64;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.util.Date;

/**
 * Convenience class created from ID Token.  Contains user info and time stamps, etc.
 * Function getValid() can be called to determine if JSON conversion succeeded.
 */
public class IdTokenPayload {
    private final static String TAG = "IdTokenPayload";
    private boolean valid = false;


    /**
     * The raw name string.
     */
    public String name = null;

    /**
     * last name
     */
    public String family_name = null;

    /**
     * first name
     */
    public String given_name = null;

    /**
     * The raw unix timestamp of the token issue time. See issuedAtTime for a Date version.
     */
    public int iat;

    /**
     * The raw unix timestamp of the session expire time. See expirationTime for a Date version.
     */
    public int exp;

    /**
     * The raw unix timestamp of the time of authentication.
     */
    public int auth_time;

    /**
     * client identificator
     */
    public String inum  = null;

    /**
     * issuer URL
     */
    public String iss  = null;

    /**
     * client ID  (aud=Audience)
     */
    public String aud  = null;

    /**
     * access token hash, can be used to validate access token.
     */
    public String at_hash = null;

    /**
     * "Authentication Context Class Reference", the auth context used to initiate session
     */
    public String acr = null;

    /**
     * OpenID connect version of the OX auth
     */
    public String oxOpenIDConnectVersion  = null;

    /**
     * The validation URI of OX auth.
     */
    public String oxValidationURI  = null;

    /**
     * The subject of the authentication. The contained string is the person resource id.
     */
    public String sub  = null;

    /**
     * Constructor.  Valid attribute set to true if almost all values are found in id token
     * payload string.
     * @param idTokenPayloadJSON  id token payload string in JSON format
     */
    public IdTokenPayload(@NonNull String idTokenPayloadJSON) {
        JSONObject jsonObject = null;
        try {
            jsonObject = new JSONObject(idTokenPayloadJSON);
        } catch (JSONException ex) {
            Log.e(TAG,"IdTokenPayload() constructor : JSON error converting string to object");
            return;
        }
        valid = true;
        try {
            iat = (int) jsonObject.get("iat");
            exp = (int) jsonObject.get("exp");
            auth_time = (int) jsonObject.get("auth_time");
            iss = (String) jsonObject.get("iss");
            aud = (String) jsonObject.get("aud");
            oxOpenIDConnectVersion = (String) jsonObject.get("oxOpenIDConnectVersion");
            oxValidationURI = (String) jsonObject.get("oxValidationURI");
            sub = (String) jsonObject.get("sub");

        } catch (JSONException ex) {
            Log.e(TAG,"IdTokenPayload() constructor: JSON error converting idTokenPayload,"+
                            " missing value", ex);
            valid = false;
        }
        try {
            name = (String) jsonObject.get("name");
            family_name = (String) jsonObject.get("family_name");
            given_name = (String) jsonObject.get("given_name");
            inum = (String) jsonObject.get("inum");
            at_hash = (String) jsonObject.get("at_hash");
            acr = (String) jsonObject.get("acr");
        } catch (JSONException ex) {
            Log.d(TAG,"IdTokenPayload() constructor: missing  at least one of : "+
                    "at_hash, acr, inum or any of names");
        }
    }

    /**
     * Is object valid?
     * @return  true if JSON conversion succeeded.
     */
    public boolean isValid() {
        return valid;
    }

    /**
     * convenience function for getting person resource id
     */
    public String getPersonResourceId() {
        return sub;
    }

    /**
     * Time of authentication.
     * @return date
     */
    public Date getAuthTime() {
        return new java.util.Date((long) auth_time *1000);
    }

    /**
     * The token issue time.
     * @return date
     */
    public Date getIssuedAtTime() {
        return new java.util.Date((long) iat *1000);
    }

    /**
     * The session expiration time.
     * @return date
     */
    public  Date getExpirationTime() {
        return new java.util.Date((long) exp *1000);
    }

    /**
     *  JWT token decoding function. Will parse a JSON string out of the base64 encoded id token.
     *  @return JSON string containing id token payload information
     */
    public static String decodeJWTToken(@Nullable String idToken) {
        Log.d(TAG, "decodeJWTToken() ");
        String payloadStr = null;
        if (idToken != null) {
            String[] parts = idToken.split("\\.");
            if (parts.length > 1) {
                String payloadBase64Str = parts[1];
                try {
                    payloadStr = new String(Base64.decode(payloadBase64Str,
                            Base64.URL_SAFE), "UTF-8");
                } catch (UnsupportedEncodingException ex) {
                    Log.e(TAG, "decodeJWTToken(): Base64 UnsupportedEncodingException when reading IdTokenPayload", ex);
                }
            } else {
                Log.e(TAG,"decodeJWTToken():  idToken split failed, parts.length=" + parts.length +
                        " idToken=" + idToken);
            }
        }

        return payloadStr;
    }

    public String serializeToJson() throws JsonSyntaxException {
        Gson gson = new GsonBuilder().create();
        return gson.toJson(this);
    }

}
