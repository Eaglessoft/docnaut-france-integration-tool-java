package com.docnaut.clienttestportal.web;

import java.nio.charset.StandardCharsets;
import java.util.List;

import com.docnaut.clienttestportal.api.ApiExchange;
import com.docnaut.clienttestportal.api.ApiRequests;
import com.docnaut.clienttestportal.api.ClientApiClient;
import com.docnaut.clienttestportal.api.ClientApiOperation;
import com.docnaut.clienttestportal.view.ApiViews;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriUtils;

@Controller
public class WebhookController {

    private static final int PAGE = 25;

    /**
     * The event types the pinned contract declares. They are contract facts, exactly like the
     * operation registry, so the form offers these and nothing else.
     */
    public static final List<String> EVENT_TYPES =
            List.of("DOCUMENT_INTAKE_ACCEPTED", "DOCUMENT_INBOUND_AVAILABLE");

    private final ClientApiClient api;

    public WebhookController(ClientApiClient api) {
        this.api = api;
    }

    @GetMapping("/webhooks")
    public String list(@RequestParam(name = "cursor", required = false) String cursor, Model model) {
        ApiExchange exchange = api.call(ApiRequests.of(ClientApiOperation.LIST_CLIENT_WEBHOOK_SUBSCRIPTIONS)
                .page(cursor, PAGE).build());
        ResultModel.apply(model, exchange);
        model.addAttribute("subscriptions", ApiViews.subscriptions(exchange.body()));
        model.addAttribute("eventTypes", EVENT_TYPES);
        return "webhooks";
    }

    /**
     * The operator fills in fields; the portal builds the body the contract declares. Whether the
     * endpoint can be verified is the API's decision to report.
     */
    @PostMapping("/webhooks")
    public String create(@RequestParam("name") String name, @RequestParam("endpoint") String endpoint,
            @RequestParam(name = "mode", defaultValue = "ALL_CLIENT_EVENTS") String mode,
            @RequestParam(name = "eventTypes", required = false) List<String> eventTypes, Model model) {
        ApiExchange exchange = api.call(ApiRequests.of(ClientApiOperation.CREATE_CLIENT_WEBHOOK_SUBSCRIPTION)
                .idempotencyKey()
                .body("{\"name\":\"" + escape(name) + "\",\"endpoint\":\"" + escape(endpoint) + "\","
                        + "\"eventSelection\":" + selection(mode, eventTypes) + "}")
                .build());
        ResultModel.apply(model, exchange);
        model.addAttribute("created", ApiViews.subscription(exchange.body()));
        model.addAttribute("issuedSecret", ApiViews.issuedSecret(exchange.body()));
        model.addAttribute("subscriptions", ApiViews.subscriptions(
                api.call(ApiRequests.of(ClientApiOperation.LIST_CLIENT_WEBHOOK_SUBSCRIPTIONS)
                        .page(null, PAGE).build()).body()));
        model.addAttribute("eventTypes", EVENT_TYPES);
        return "webhooks";
    }

    /** Same plain-form navigation as documents: no client-side scripting is required. */
    @GetMapping("/webhooks/open")
    public String open(@RequestParam("subscriptionId") String subscriptionId) {
        return "redirect:/webhooks/" + UriUtils.encodePathSegment(subscriptionId.trim(), StandardCharsets.UTF_8);
    }

    @GetMapping("/webhooks/{subscriptionId}")
    public String detail(@PathVariable("subscriptionId") String subscriptionId, Model model) {
        ApiExchange exchange = api.call(ApiRequests.of(ClientApiOperation.GET_CLIENT_WEBHOOK_SUBSCRIPTION)
                .path(subscriptionId).build());
        ResultModel.apply(model, exchange);
        model.addAttribute("subscriptionId", subscriptionId);
        model.addAttribute("subscription", ApiViews.subscription(exchange.body()));
        model.addAttribute("eventTypes", EVENT_TYPES);
        return "webhook-detail";
    }

    /**
     * The contract requires {@code If-Match} here and declares no ETag on the webhook reads, so the
     * validator is the version the screen just read. The operator never types an entity tag.
     */
    @PostMapping("/webhooks/{subscriptionId}")
    public String update(@PathVariable("subscriptionId") String subscriptionId,
            @RequestParam("version") long version, @RequestParam("name") String name,
            @RequestParam("endpoint") String endpoint,
            @RequestParam(name = "mode", defaultValue = "ALL_CLIENT_EVENTS") String mode,
            @RequestParam(name = "eventTypes", required = false) List<String> eventTypes,
            @RequestParam(name = "active", required = false) Boolean active, Model model) {
        String body = "{\"name\":\"" + escape(name) + "\",\"endpoint\":\"" + escape(endpoint) + "\","
                + "\"eventSelection\":" + selection(mode, eventTypes)
                + (active == null ? "" : ",\"active\":" + active) + "}";
        ApiExchange exchange = api.call(ApiRequests.of(ClientApiOperation.UPDATE_CLIENT_WEBHOOK_SUBSCRIPTION)
                .path(subscriptionId).header("If-Match", "\"" + version + "\"").body(body).build());
        return afterAction(subscriptionId, exchange, model);
    }

    /** Verification is asynchronous: a 202 means started, never active. */
    @PostMapping("/webhooks/{subscriptionId}/verify")
    public String verify(@PathVariable("subscriptionId") String subscriptionId, Model model) {
        ApiExchange exchange = api.call(ApiRequests.of(ClientApiOperation.VERIFY_CLIENT_WEBHOOK_SUBSCRIPTION)
                .path(subscriptionId).idempotencyKey().body("{}").build());
        model.addAttribute("verificationStarted", exchange.successful());
        return afterAction(subscriptionId, exchange, model);
    }

    @PostMapping("/webhooks/{subscriptionId}/rotate-secret")
    public String rotate(@PathVariable("subscriptionId") String subscriptionId,
            @RequestParam(name = "reason", required = false) String reason, Model model) {
        ApiExchange exchange = api.call(ApiRequests.of(ClientApiOperation.ROTATE_CLIENT_WEBHOOK_SECRET)
                .path(subscriptionId)
                .body(reason == null || reason.isBlank() ? "{}" : "{\"reason\":\"" + escape(reason) + "\"}")
                .build());
        model.addAttribute("issuedSecret", ApiViews.issuedSecret(exchange.body()));
        return afterAction(subscriptionId, exchange, model);
    }

    /**
     * A mutation is shown against the subscription as it now stands, so the screen never displays
     * a version the operator would go on to send back.
     */
    private String afterAction(String subscriptionId, ApiExchange action, Model model) {
        ApiExchange current = api.call(ApiRequests.of(ClientApiOperation.GET_CLIENT_WEBHOOK_SUBSCRIPTION)
                .path(subscriptionId).build());
        ResultModel.apply(model, action, current);
        model.addAttribute("subscriptionId", subscriptionId);
        model.addAttribute("subscription", ApiViews.subscription(current.body()));
        model.addAttribute("eventTypes", EVENT_TYPES);
        return "webhook-detail";
    }

    private static String selection(String mode, List<String> eventTypes) {
        if (!"SELECTED".equals(mode)) {
            return "{\"mode\":\"ALL_CLIENT_EVENTS\"}";
        }
        List<String> chosen = eventTypes == null ? List.of() : eventTypes.stream().filter(EVENT_TYPES::contains).toList();
        StringBuilder types = new StringBuilder();
        for (String type : chosen) {
            types.append(types.isEmpty() ? "" : ",").append('"').append(type).append('"');
        }
        return "{\"mode\":\"SELECTED\",\"eventTypes\":[" + types + "]}";
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", " ").replace("\r", " ");
    }
}
