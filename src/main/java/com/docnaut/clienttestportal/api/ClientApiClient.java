package com.docnaut.clienttestportal.api;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import com.docnaut.clienttestportal.config.PortalProperties;
import com.docnaut.clienttestportal.security.ServiceClientAuthentication;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * The single place that speaks HTTP to the Client API.
 *
 * <p>Only the operations in {@link ClientApiOperation} are reachable, the origin is fixed, and
 * redirects are refused at the transport layer. Every outcome, including a rejection, comes back
 * as an {@link ApiExchange} for the interface to render.
 */
@Component
public class ClientApiClient implements ClientApiGateway {


    private static final String CORRELATION_HEADER = "X-Correlation-ID";

    private final Map<PortalProperties.Environment, RestClient> clients = new java.util.EnumMap<>(PortalProperties.Environment.class);
    private final TokenClient tokenClient;

    public ClientApiClient(RestClient.Builder builder, PortalProperties properties, TokenClient tokenClient) {
        this.tokenClient = tokenClient;
        HttpClient httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(properties.readTimeout());
        for (var environment : PortalProperties.Environment.values())
            clients.put(environment, builder.clone().baseUrl(properties.endpoint(environment).apiBaseUrl().toString())
                    .requestFactory(factory).build());
    }

    @Override
    public ApiExchange call(ApiRequest request) {
        long started = System.nanoTime();
        try {
            return currentClient().method(request.operation().method())
                    .uri(uriBuilder -> {
                        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(
                                request.operation().pathTemplate().substring(1));
                        request.query().forEach((name, values) -> values.forEach(
                                value -> builder.queryParam(name, value)));
                        return uriBuilder.replaceQuery(null)
                                .path(builder.build().getPath())
                                .query(builder.build().getQuery())
                                .build(request.pathVariables());
                    })
                    .headers(headers -> applyHeaders(headers, request))
                    .body(request.body() == null ? "" : request.body())
                    .exchange((outgoing, response) -> read(request.operation(), response, elapsed(started)), false);
        } catch (RestClientException | IllegalStateException failure) {
            return ApiExchange.failure(request.operation(), elapsed(started), describe(failure));
        }
    }

    /**
     * Streams artefact bytes straight through to the operator's browser. The body is never
     * buffered, retained, logged or rendered.
     */
    @Override
    public ApiExchange streamArtifact(ApiRequest request, HttpServletResponse target) {
        long started = System.nanoTime();
        try {
            return currentClient().method(request.operation().method())
                    .uri(uriBuilder -> uriBuilder
                            .path(request.operation().pathTemplate().substring(1))
                            .build(request.pathVariables()))
                    .headers(headers -> applyHeaders(headers, request))
                    .exchange((outgoing, response) -> {
                        int status = response.getStatusCode().value();
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            return read(request.operation(), response, elapsed(started));
                        }
                        copyDownloadHeaders(response.getHeaders(), target);
                        try (InputStream source = response.getBody(); OutputStream sink = target.getOutputStream()) {
                            source.transferTo(sink);
                            sink.flush();
                        }
                        return new ApiExchange(request.operation(), status, true,
                                response.getHeaders().getFirst(CORRELATION_HEADER), elapsed(started),
                                null, shownHeaders(response.getHeaders()), null);
                    }, false);
        } catch (RestClientException | IllegalStateException | UncheckedTransferException failure) {
            return ApiExchange.failure(request.operation(), elapsed(started), describe(failure));
        }
    }

    private RestClient currentClient() {
        if (!(SecurityContextHolder.getContext().getAuthentication() instanceof ServiceClientAuthentication authentication))
            throw new IllegalStateException("Sign in before sending a request.");
        return clients.get(authentication.getEnvironment());
    }

    private void applyHeaders(HttpHeaders headers, ApiRequest request) {
        headers.setBearerAuth(currentToken());
        request.headers().forEach(headers::set);
        if (request.body() != null && headers.getContentType() == null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
    }

    private String currentToken() {
        if (!(SecurityContextHolder.getContext().getAuthentication()
                instanceof ServiceClientAuthentication authentication)) {
            throw new IllegalStateException("No signed-in service client");
        }
        TokenClient.Token token = authentication.token();
        if (token == null || token.stale()) {
            token = tokenClient.obtain(authentication.getEnvironment(), authentication.clientId(), authentication.clientSecret());
            authentication.replaceToken(token);
        }
        return token.value();
    }

    private static ApiExchange read(ClientApiOperation operation,
            org.springframework.http.client.ClientHttpResponse response, long durationMillis) throws IOException {
        int status = response.getStatusCode().value();
        String body = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
        return new ApiExchange(operation, status, response.getStatusCode().is2xxSuccessful(),
                response.getHeaders().getFirst(CORRELATION_HEADER), durationMillis,
                body.isBlank() ? null : body, shownHeaders(response.getHeaders()), null);
    }

    private static Map<String, String> shownHeaders(HttpHeaders headers) {
        Map<String, String> shown = new LinkedHashMap<>();
        for (String name : ApiExchange.SHOWN_HEADERS) {
            String value = headers.getFirst(name);
            if (value != null) {
                shown.put(name, value);
            }
        }
        return shown;
    }

    private static void copyDownloadHeaders(HttpHeaders source, HttpServletResponse target) {
        target.setStatus(HttpServletResponse.SC_OK);
        copy(source, target, HttpHeaders.CONTENT_TYPE);
        copy(source, target, HttpHeaders.CONTENT_LENGTH);
        copy(source, target, HttpHeaders.CONTENT_DISPOSITION);
        target.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        target.setHeader(HttpHeaders.PRAGMA, "no-cache");
    }

    private static void copy(HttpHeaders source, HttpServletResponse target, String name) {
        String value = source.getFirst(name);
        if (value != null) {
            target.setHeader(name, value);
        }
    }

    private static long elapsed(long startedNanos) {
        return Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
    }

    /** Transport problems are shown by class and message only; no header or body is echoed. */
    private static String describe(Exception failure) {
        Throwable root = failure;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName() + ": " + root.getMessage();
    }

    static final class UncheckedTransferException extends RuntimeException {
        @java.io.Serial
        private static final long serialVersionUID = 1L;

        UncheckedTransferException(IOException cause) {
            super(cause);
        }
    }

    /** A request the portal is allowed to build. */
    public record ApiRequest(ClientApiOperation operation, Object[] pathVariables,
            MultiValueMap<String, String> query, Map<String, String> headers, Object body) {
    }
}
