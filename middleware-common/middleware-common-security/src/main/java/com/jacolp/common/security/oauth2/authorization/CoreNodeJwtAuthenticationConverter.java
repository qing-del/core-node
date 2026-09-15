package com.jacolp.common.security.oauth2.authorization;

import com.jacolp.common.security.oauth2.jwt.CoreNodeAccessTokenClaimsValidator;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Maps one already-validated CORE NODE access token into explicit role and raw-scope authorities. */
public final class CoreNodeJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final OAuth2Error INVALID_TOKEN = new OAuth2Error(
            "invalid_token", "JWT access token claims are invalid", null);

    private final CoreNodeAccessTokenClaimsValidator claimsValidator;

    public CoreNodeJwtAuthenticationConverter(CoreNodeAccessTokenClaimsValidator claimsValidator) {
        this.claimsValidator = Objects.requireNonNull(claimsValidator, "claimsValidator must not be null");
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        if (claimsValidator.validate(jwt).hasErrors()) {
            throw new OAuth2AuthenticationException(INVALID_TOKEN);
        }
        String role = jwt.getClaimAsStringList("roles").getFirst();
        List<String> scopes = jwt.getClaimAsStringList("scope");
        List<GrantedAuthority> authorities = new ArrayList<>(scopes.size() + 1);
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
        scopes.stream().sorted(Comparator.naturalOrder())
                .map(scope -> new SimpleGrantedAuthority("SCOPE_" + scope))
                .forEach(authorities::add);
        return new JwtAuthenticationToken(jwt, List.copyOf(authorities), jwt.getSubject());
    }
}
