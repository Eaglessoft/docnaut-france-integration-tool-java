package com.docnaut.clienttestportal.web;

import com.docnaut.clienttestportal.api.ApiExchange;
import com.docnaut.clienttestportal.api.ApiRequests;
import com.docnaut.clienttestportal.api.ClientApiClient;
import com.docnaut.clienttestportal.api.ClientApiOperation;
import com.docnaut.clienttestportal.view.ApiViews;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The two settings this tenant owns: the preferences the Client API exposes, and the credential
 * this session signed in with.
 */
@Controller
public class SettingsController {

    private final ClientApiClient api;

    public SettingsController(ClientApiClient api) {
        this.api = api;
    }

    @GetMapping("/settings")
    public String settings(Model model) {
        return read(model, null, null);
    }

    /**
     * The contract requires {@code If-Match}. The validator is the ETag of the read this screen was
     * built from, carried in a hidden field; a stale one is the API's conflict to report.
     */
    @PostMapping("/settings/configuration")
    public String replaceConfiguration(@RequestParam("ifMatch") String ifMatch,
            @RequestParam("preferredLocale") String preferredLocale,
            @RequestParam("preferredTimeZone") String preferredTimeZone, Model model) {
        ApiExchange exchange = api.call(ApiRequests.of(ClientApiOperation.REPLACE_CLIENT_CONFIGURATION)
                .header("If-Match", ifMatch)
                .body("{\"preferredLocale\":\"" + escape(preferredLocale) + "\",\"preferredTimeZone\":\""
                        + escape(preferredTimeZone) + "\"}")
                .build());
        model.addAttribute("configurationSaved", exchange.successful());
        return read(model, exchange, null);
    }

    /**
     * Rotation replaces the very credential this session signed in with. The new secret is shown
     * once and the operator signs in again with it; the portal never retries or stores it.
     */
    @PostMapping("/settings/rotate-secret")
    public String rotateSecret(@RequestParam("reason") String reason, Model model) {
        ApiExchange exchange = api.call(ApiRequests.of(ClientApiOperation.ROTATE_OWN_SERVICE_CLIENT_SECRET)
                .body("{\"reason\":\"" + escape(reason) + "\"}")
                .build());
        model.addAttribute("rotatedSecret", ApiViews.issuedSecret(exchange.body()));
        return read(model, null, exchange);
    }

    /** Every route lands on the same screen, rebuilt from a fresh read so the ETag is never stale. */
    private String read(Model model, ApiExchange configurationAction, ApiExchange rotation) {
        ApiExchange configuration = api.call(ApiRequests.of(ClientApiOperation.GET_CLIENT_CONFIGURATION).build());
        ApiExchange profile = api.call(ApiRequests.of(ClientApiOperation.GET_CLIENT_PROFILE).build());
        ResultModel.apply(model, configurationAction, rotation, configuration, profile);
        model.addAttribute("configuration", ApiViews.configuration(configuration.body()));
        model.addAttribute("configurationExchange", configuration);
        model.addAttribute("etag", configuration.header("ETag"));
        model.addAttribute("profile", ApiViews.profile(profile.body()));
        return "settings";
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", " ").replace("\r", " ");
    }
}
