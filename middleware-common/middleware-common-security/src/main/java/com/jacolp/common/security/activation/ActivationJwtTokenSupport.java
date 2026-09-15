package com.jacolp.common.security.activation;

import com.jacolp.common.security.token.SecurityTokenConstants;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/** HS256 support for the activation-link credential exception only. */
public final class ActivationJwtTokenSupport {

    private ActivationJwtTokenSupport() {
    }

    public static String createActivationJwt(String secretKey, long ttlMillis, Map<String, Object> claims) {
        return Jwts.builder()
                .setClaims(new HashMap<>(claims))
                .signWith(SignatureAlgorithm.HS256, secretKey.getBytes(StandardCharsets.UTF_8))
                .setExpiration(new Date(System.currentTimeMillis() + ttlMillis))
                .compact();
    }

    public static Claims parseActivationJwt(String secretKey, String token) {
        return Jwts.parser()
                .setSigningKey(secretKey.getBytes(StandardCharsets.UTF_8))
                .parseClaimsJws(token)
                .getBody();
    }

    public static String issueActivationToken(String secretKey, long ttlMillis, Long userId) {
        return createActivationJwt(secretKey, ttlMillis, Map.of(
                SecurityTokenConstants.ACTIVE_SIGN_KEY, true,
                SecurityTokenConstants.USER_ID_CLAIM, userId));
    }
}
