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
/* code has been modified by Nixu Oy */

package org.vaultit.mobilesso.mobilessosdk;

import android.net.Uri;
import android.support.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import org.vaultit.mobilesso.mobilessosdk.Util.GsonUriAdapter;


/**
 * An identity provider
 *
 *  Parameters for creating an instance:
 *  - discoveryEndpoint (Environment's OpenID Connect Provider Discovery URL), e.g.
 *        "https://nordic-eid-gluu.qvarnlabs.net/.well-known/openid-configuration"
 *  - clientId (app client id, generated when registering an application for Nordic eID), e.g.
 *        "@!2027.863B.4505.5985!0001!200B.B2FE!0008!74FE.DE66"
 *  - clientSecret (app client secret, generated when registering an application for Nordic eID), e.g.
 *        "epbqEmD3AkROCdPsxBSe"
 *  - redirectUri (Application redirect URL after login), e.g.
 *        "com.example.virtualcard.auth://oidc_callback"
 *  - logoutRedirectUri (Application redirect URL after logout), e.g.
 *        "com.example.virtualcard.logout://oidc_callback"
 *  - scope (OpenID scopes for application), e.g.
 *         "openid profile email vcbe_virtual_cards_post vcbe_virtual_cards_delete gluu_fido_u2f_revoke"
 */
public class IdentityProvider {

    /**
     * Value used to indicate that a configured property is not specified or required.
     */
    private static final int NOT_SPECIFIED = -1;

    private Uri mDiscoveryEndpoint;
    private String mClientId;
    private String mClientSecret;
    private Uri mRedirectUri;
    private Uri mLogoutRedirectUri;
    private String mScope;

    public IdentityProvider(
            String discoveryEndpoint,
            String clientId,
            String clientSecret,
            String redirectUri,
            String logoutredirectUri,
            String scope) {

        if (!isSpecified(discoveryEndpoint)) {
            throw new IllegalArgumentException(
                    "the discovery endpoint must be specified");
        }

        this.mDiscoveryEndpoint = Uri.parse(checkSpecified(discoveryEndpoint,"discoveryEndpoint"));
        this.mClientId = checkSpecified(clientId,"clientId");
        this.mClientSecret = checkSpecified(clientSecret,"clientSecret");
        this.mRedirectUri = Uri.parse(checkSpecified(redirectUri, "redirectUri"));
        this.mLogoutRedirectUri = Uri.parse(checkSpecified(logoutredirectUri, "logoutredirectUri"));
        this.mScope = checkSpecified(scope, "scope");
    }


    @NonNull
    public Uri getDiscoveryEndpoint() {
        return mDiscoveryEndpoint;
    }

    @NonNull
    public String getClientId() {
        return mClientId;
    }

    @NonNull
    public String getClientSecret() {
        return mClientSecret;
    }

    public void setClientId(String clientId) {
        mClientId = clientId;
    }

    public void setClientSecret(String clientSecret) {
        mClientSecret = clientSecret;
    }

    @NonNull
    public Uri getRedirectUri() {
        return mRedirectUri;
    }

    @NonNull
    public Uri getLogoutRedirectUri() {
        return mLogoutRedirectUri;
    }

    public void setLogoutRedirectUri(String logoutRedirectUri) {
        mLogoutRedirectUri =  Uri.parse(logoutRedirectUri);
    }

    @NonNull
    public String getScope() {
        return mScope;
    }

    private static boolean isSpecified(int value) {
        return value != NOT_SPECIFIED;
    }
    private static boolean isSpecified(String value) {
        return !value.isEmpty();
    }

    private static int checkSpecified(int value, String valueName) {
        if (value == NOT_SPECIFIED) {
            throw new IllegalArgumentException(valueName + " must be specified");
        }
        return value;
    }
    private static String checkSpecified(String value, String valueName) {
        if (value == null || value.isEmpty() || value.equalsIgnoreCase("")) {
            throw new IllegalArgumentException(valueName + " must be specified");
        }
        return value;
    }

    public String serializeToJson() {
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(Uri.class, new GsonUriAdapter())
                .create();
        return gson.toJson(this);
    }

    public static IdentityProvider deserializeFromJson(String jsonString)
            throws JsonSyntaxException {
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(Uri.class, new GsonUriAdapter())
                .create();
        return gson.fromJson(jsonString, IdentityProvider.class);
    }
}

