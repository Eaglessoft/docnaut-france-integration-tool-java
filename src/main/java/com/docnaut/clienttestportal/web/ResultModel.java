package com.docnaut.clienttestportal.web;

import java.util.ArrayList;
import java.util.List;

import com.docnaut.clienttestportal.api.ApiExchange;
import org.springframework.ui.Model;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Puts the calls a screen made on the page.
 *
 * <p>A screen renders the outcome in its own terms and keeps every exchange behind it: the status,
 * the correlation id and the untouched body stay one click away, because on a test portal that
 * evidence is the point. The first call is the screen's primary one.
 */
public final class ResultModel {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private ResultModel() {
    }

    public static void apply(Model model, ApiExchange... exchanges) {
        List<ApiExchange> made = new ArrayList<>();
        for (ApiExchange exchange : exchanges) {
            if (exchange != null) {
                made.add(exchange);
            }
        }
        ApiExchange primary = made.isEmpty() ? null : made.getFirst();
        model.addAttribute("exchanges", List.copyOf(made));
        model.addAttribute("exchange", primary);
        model.addAttribute("prettyBody", primary == null ? null : pretty(primary.body()));
        model.addAttribute("nextCursor", primary == null ? null : nextCursor(primary.body()));
    }

    static String pretty(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(MAPPER.readTree(body));
        } catch (JacksonException | IllegalArgumentException notJson) {
            return body;
        }
    }

    /**
     * The cursor is an opaque tenant-bound token the API issues, never a page number the operator
     * can invent. Lifting it out of the page response lets the paging control carry the real value.
     */
    static String nextCursor(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode cursor = MAPPER.readTree(body).path("nextCursor");
            return cursor.isString() && !cursor.stringValue().isBlank() ? cursor.stringValue() : null;
        } catch (JacksonException | IllegalArgumentException notJson) {
            return null;
        }
    }
}
