package com.docnaut.clienttestportal.config;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Fixed operator-configured environments; login never accepts arbitrary URLs. */
@ConfigurationProperties(prefix = "portal")
public record PortalProperties(Endpoint sandbox, Endpoint production,
        @DefaultValue("10s") Duration connectTimeout, @DefaultValue("60s") Duration readTimeout) {
    public enum Environment { Sandbox, Production }
    public Endpoint endpoint(Environment environment) {
        return environment == Environment.Sandbox ? sandbox : production;
    }
    public record Endpoint(URI apiBaseUrl, URI tokenUrl, @DefaultValue("false") boolean allowInsecureHttp) {
        public Endpoint {
            requireSafe(apiBaseUrl, allowInsecureHttp);
            requireSafe(tokenUrl, allowInsecureHttp);
            if (!apiBaseUrl.getPath().endsWith("/client/api/v1/"))
                throw new IllegalStateException("API base URL must end with /client/api/v1/");
        }
        private static void requireSafe(URI uri, boolean allowHttp) {
            if (uri == null || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null)
                throw new IllegalStateException("Endpoint must be an absolute URL without credentials, query or fragment");
            if ("https".equalsIgnoreCase(uri.getScheme())) return;
            if ("http".equalsIgnoreCase(uri.getScheme()) && allowHttp
                    && java.util.Set.of("localhost", "127.0.0.1", "[::1]", "::1").contains(uri.getHost())) return;
            throw new IllegalStateException("Endpoint requires HTTPS; explicit development HTTP is limited to loopback");
        }
    }
}
