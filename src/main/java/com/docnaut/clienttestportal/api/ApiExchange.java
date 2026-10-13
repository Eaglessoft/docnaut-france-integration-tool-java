package com.docnaut.clienttestportal.api;

import java.util.List;
import java.util.Map;

/**
 * One completed call to the Client API, in the shape the user interface renders it.
 *
 * <p>The portal displays what the API returned. It does not reclassify a status, retry a
 * mutation or translate an API decision into a portal rule.
 */
public record ApiExchange(ClientApiOperation operation, int status, boolean successful, String correlationId,
        long durationMillis, String body, Map<String, String> shownHeaders, String transportFailure) {

    /** Response headers worth showing next to a result. Authorization is never among them. */
    public static final List<String> SHOWN_HEADERS =
            List.of("ETag", "Location", "Content-Type", "Content-Disposition", "X-Correlation-ID");

    public static ApiExchange failure(ClientApiOperation operation, long durationMillis, String reason) {
        return new ApiExchange(operation, 0, false, null, durationMillis, null, Map.of(), reason);
    }

    public boolean unreachable() {
        return transportFailure != null;
    }

    public String header(String name) {
        return shownHeaders.get(name);
    }
}
