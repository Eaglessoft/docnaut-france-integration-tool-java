package com.docnaut.clienttestportal.web;

import com.docnaut.clienttestportal.api.ApiExchange;
import com.docnaut.clienttestportal.api.ApiRequests;
import com.docnaut.clienttestportal.api.ClientApiClient;
import com.docnaut.clienttestportal.api.ClientApiOperation;
import com.docnaut.clienttestportal.view.ApiViews;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SessionController {

    /** Enough recent work to orient the operator without turning the landing page into a report. */
    private static final int RECENT = 5;

    private final ClientApiClient api;

    public SessionController(ClientApiClient api) {
        this.api = api;
    }

    @GetMapping("/peppol-facts")
    public String peppolFacts(@org.springframework.web.bind.annotation.RequestParam(required = false) String cursor,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "25") Integer limit, Model model) {
        ResultModel.apply(model, api.call(ApiRequests.of(ClientApiOperation.LIST_PEPPOL_FACTS).page(cursor, limit).build()));
        return "peppol-facts";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    /**
     * Everything here follows from the credentials alone. Each panel reports its own call, so a
     * permission these credentials lack shows up as that call's rejection instead of an empty page.
     */
    @GetMapping("/")
    public String overview(Model model) {
        ApiExchange profile = api.call(ApiRequests.of(ClientApiOperation.GET_CLIENT_PROFILE).build());
        ApiExchange documents = api.call(ApiRequests.of(ClientApiOperation.LIST_DOCUMENTS)
                .page(null, RECENT).build());
        ApiExchange subscriptions = api.call(ApiRequests.of(ClientApiOperation.LIST_CLIENT_WEBHOOK_SUBSCRIPTIONS)
                .page(null, RECENT).build());
        ResultModel.apply(model, profile, documents, subscriptions);
        model.addAttribute("profile", ApiViews.profile(profile.body()));
        model.addAttribute("profileExchange", profile);
        model.addAttribute("documents", ApiViews.documents(documents.body()));
        model.addAttribute("documentsExchange", documents);
        model.addAttribute("subscriptions", ApiViews.subscriptions(subscriptions.body()));
        model.addAttribute("subscriptionsExchange", subscriptions);
        return "overview";
    }
}
