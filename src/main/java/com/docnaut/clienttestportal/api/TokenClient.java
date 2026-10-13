package com.docnaut.clienttestportal.api;

import java.time.Duration;
import java.time.Instant;

import com.docnaut.clienttestportal.config.PortalProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Exchanges the operator-supplied client credentials for an access token at the environment's
 * identity provider.
 *
 * <p>The portal never inspects, validates or introspects the token. It obtains one, sends it, and
 * lets the Client API decide whether it is acceptable.
 */
@Component
public class TokenClient {

    /** Renew slightly before the identity provider's own expiry so a call does not race it. */
    private static final Duration EARLY_RENEWAL = Duration.ofSeconds(30);

    private final java.util.Map<PortalProperties.Environment, RestClient> clients = new java.util.EnumMap<>(PortalProperties.Environment.class);

    public TokenClient(RestClient.Builder builder, PortalProperties properties) {
        var http = java.net.http.HttpClient.newBuilder()
                .followRedirects(java.net.http.HttpClient.Redirect.NEVER)
                .connectTimeout(properties.connectTimeout()).build();
        var factory = new org.springframework.http.client.JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(properties.readTimeout());
        for (var environment : PortalProperties.Environment.values())
            clients.put(environment, builder.clone().baseUrl(properties.endpoint(environment).tokenUrl().toString())
                    .requestFactory(factory).build());
    }

    public Token obtain(PortalProperties.Environment environment, String clientId, String clientSecret) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        TokenResponse response = clients.get(environment).post()
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(TokenResponse.class);
        if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
            throw new TokenUnavailableException("The identity provider returned no access token.");
        }
        long lifetime = response.expiresIn() > 0 ? response.expiresIn() : 60L;
        return new Token(response.accessToken(), Instant.now().plusSeconds(lifetime).minus(EARLY_RENEWAL));
    }

    /** An access token and the instant after which the portal asks for a new one. */
    public record Token(String value, Instant renewAfter) {
        public boolean stale() {
            return Instant.now().isAfter(renewAfter);
        }
    }

    record TokenResponse(String access_token, long expires_in) {
        String accessToken() {
            return access_token;
        }

        long expiresIn() {
            return expires_in;
        }
    }

    public static class TokenUnavailableException extends RuntimeException {
        @java.io.Serial
        private static final long serialVersionUID = 1L;

        public TokenUnavailableException(String message) {
            super(message);
        }
    }
}
