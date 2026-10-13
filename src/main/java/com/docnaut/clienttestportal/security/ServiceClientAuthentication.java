package com.docnaut.clienttestportal.security;

import java.io.Serial;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.docnaut.clienttestportal.api.TokenClient;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * The signed-in operator, which is really a set of service-client credentials.
 *
 * <p>The client secret and the access token live here, inside the server-side session, and are
 * never exposed to a template, a URL, a log or any durable state. {@link #eraseCredentials()} is
 * deliberately not honoured for the secret because the portal must renew tokens for the session.
 */
public final class ServiceClientAuthentication extends AbstractAuthenticationToken {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final String AUTHORITY = "PORTAL_OPERATOR";

    private final com.docnaut.clienttestportal.config.PortalProperties.Environment environment;
    public com.docnaut.clienttestportal.config.PortalProperties.Environment getEnvironment() { return environment; }

    private final String clientId;
    private final String clientSecret;
    private final transient AtomicReference<TokenClient.Token> token = new AtomicReference<>();

    public ServiceClientAuthentication(String clientId, String clientSecret, TokenClient.Token token,
            com.docnaut.clienttestportal.config.PortalProperties.Environment environment) {
        super(List.of(new SimpleGrantedAuthority(AUTHORITY)));
        this.environment = environment;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.token.set(token);
        setAuthenticated(true);
    }

    public String clientId() {
        return clientId;
    }

    public String clientSecret() {
        return clientSecret;
    }

    public TokenClient.Token token() {
        return token.get();
    }

    public void replaceToken(TokenClient.Token replacement) {
        token.set(replacement);
    }

    @Override
    public Object getCredentials() {
        // Never surfaced: the secret is reachable only through clientSecret() inside the portal.
        return "";
    }

    @Override
    public Object getPrincipal() {
        return clientId;
    }

    @Override
    public String getName() {
        return clientId;
    }

    @Override
    public void eraseCredentials() {
        // Intentionally empty. The secret is required for the session's token renewals.
    }
}
