package com.jacolp.common.security.oauth2.token;

/** Atomically revokes an access token and its current client-user refresh session. */
public interface OAuth2SessionRevocationStore {

    boolean revoke(OAuth2SessionRevocationRequest request);
}
