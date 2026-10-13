package com.docnaut.clienttestportal.api;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/** Builds the only request shapes the portal is allowed to send. */
public final class ApiRequests {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder KEY_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final ClientApiOperation operation;
    private Object[] pathVariables = new Object[0];
    private final MultiValueMap<String, String> query = new LinkedMultiValueMap<>();
    private final Map<String, String> headers = new LinkedHashMap<>();
    private Object body;

    private ApiRequests(ClientApiOperation operation) {
        this.operation = operation;
    }

    public static ApiRequests of(ClientApiOperation operation) {
        return new ApiRequests(operation);
    }

    public ApiRequests path(Object... values) {
        this.pathVariables = values;
        return this;
    }

    public ApiRequests page(String cursor, Integer limit) {
        if (cursor != null && !cursor.isBlank()) {
            query.add("cursor", cursor);
        }
        if (limit != null) {
            query.add("limit", String.valueOf(limit));
        }
        return this;
    }

    public ApiRequests query(String name, String value) {
        if (value != null && !value.isBlank()) {
            query.add(name, value);
        }
        return this;
    }

    public ApiRequests header(String name, String value) {
        if (value != null && !value.isBlank()) {
            headers.put(name, value);
        }
        return this;
    }

    /**
     * The contract requires an idempotency key on document intake, webhook creation and webhook
     * verification. The portal generates a conforming key; the operator is never asked for one.
     */
    public ApiRequests idempotencyKey() {
        byte[] material = new byte[24];
        RANDOM.nextBytes(material);
        headers.put("Idempotency-Key", KEY_ENCODER.encodeToString(material));
        return this;
    }

    /** Carries a UUID generated with the rendered decision form so browser retries reuse it. */
    public ApiRequests idempotencyKey(UUID value) {
        if (value != null) {
            headers.put("Idempotency-Key", value.toString());
        }
        return this;
    }

    public ApiRequests body(Object value) {
        this.body = value;
        return this;
    }

    public ClientApiClient.ApiRequest build() {
        return new ClientApiClient.ApiRequest(operation, pathVariables, query, headers, body);
    }
}
