package com.docnaut.clienttestportal.security;

import com.docnaut.clienttestportal.api.TokenClient;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

/**
 * Signing in means proving the credentials can obtain a token. Nothing about the token's contents
 * is examined; whether the Client API then accepts it is the API's decision to report.
 */
@Component
public class ServiceClientAuthenticationProvider implements AuthenticationProvider {

    private final TokenClient tokenClient;

    public ServiceClientAuthenticationProvider(TokenClient tokenClient) {
        this.tokenClient = tokenClient;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String clientId = authentication.getName();
        String clientSecret = String.valueOf(authentication.getCredentials());
        if (clientId == null || clientId.isBlank() || clientSecret.isBlank()) {
            throw new BadCredentialsException("Enter a client id and a client secret.");
        }
        try {
            var environment = com.docnaut.clienttestportal.config.PortalProperties.Environment.valueOf(
                    String.valueOf(authentication.getDetails()));
            TokenClient.Token token = tokenClient.obtain(environment, clientId, clientSecret);
            return new ServiceClientAuthentication(clientId, clientSecret, token, environment);
        } catch (TokenClient.TokenUnavailableException | RestClientException | IllegalArgumentException failure) {
            throw new BadCredentialsException(
                    "The identity provider rejected these credentials or could not be reached.", failure);
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
