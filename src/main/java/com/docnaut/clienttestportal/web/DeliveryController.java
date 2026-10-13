package com.docnaut.clienttestportal.web;

import java.nio.charset.StandardCharsets;

import com.docnaut.clienttestportal.api.ApiExchange;
import com.docnaut.clienttestportal.api.ApiRequests;
import com.docnaut.clienttestportal.api.ClientApiClient;
import com.docnaut.clienttestportal.api.ClientApiOperation;
import com.docnaut.clienttestportal.view.ApiViews;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriUtils;

@Controller
public class DeliveryController {

    private static final int PAGE = 25;

    private final ClientApiClient api;

    public DeliveryController(ClientApiClient api) {
        this.api = api;
    }

    @GetMapping("/webhook-deliveries")
    public String list(@RequestParam(name = "subscriptionId", required = false) String subscriptionId,
            @RequestParam(name = "cursor", required = false) String cursor, Model model) {
        ApiExchange exchange = api.call(ApiRequests.of(ClientApiOperation.LIST_CLIENT_WEBHOOK_DELIVERIES)
                .query("subscriptionId", subscriptionId).page(cursor, PAGE).build());
        ResultModel.apply(model, exchange);
        model.addAttribute("deliveries", ApiViews.deliveries(exchange.body()));
        model.addAttribute("subscriptionId", subscriptionId);
        return "deliveries";
    }

    @GetMapping("/webhook-deliveries/open")
    public String open(@RequestParam("deliveryId") String deliveryId) {
        return "redirect:/webhook-deliveries/"
                + UriUtils.encodePathSegment(deliveryId.trim(), StandardCharsets.UTF_8);
    }

    @GetMapping("/webhook-deliveries/{deliveryId}")
    public String detail(@PathVariable("deliveryId") String deliveryId, Model model) {
        ApiExchange exchange = api.call(ApiRequests.of(ClientApiOperation.GET_CLIENT_WEBHOOK_DELIVERY)
                .path(deliveryId).build());
        ResultModel.apply(model, exchange);
        model.addAttribute("deliveryId", deliveryId);
        model.addAttribute("delivery", ApiViews.delivery(exchange.body()));
        return "delivery-detail";
    }
}
