package com.docnaut.clienttestportal.web;

import com.docnaut.clienttestportal.api.*;
import com.docnaut.clienttestportal.view.ApiViews;
import java.io.IOException;
import java.util.*;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** Business forms call the authenticated Client API; the API owns all eligibility and state rules. */
@Controller
public class ReportingController {
    private final ClientApiGateway api;
    public ReportingController(ClientApiGateway api) { this.api = api; }

    @GetMapping("/reporting")
    public String reporting(Model model) {
        if (!model.containsAttribute("fields")) model.addAttribute("fields", Map.of());
        if (!model.containsAttribute("idempotencyKey")) model.addAttribute("idempotencyKey", UUID.randomUUID());
        return "reporting";
    }

    @PostMapping("/reporting")
    public String submit(@RequestParam MultiValueMap<String, String> fields,
            @RequestParam("source") MultipartFile source, Model model) throws IOException {
        model.addAttribute("fields", fields.toSingleValueMap());
        model.addAttribute("idempotencyKey", fields.getFirst("idempotencyKey"));
        UUID key = key(fields, model);
        if (key == null) return reporting(model);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("category", fields.getFirst("category"));
        metadata.put("kind", fields.getFirst("kind"));
        String previous = fields.getFirst("previousReportId");
        if (previous != null && !previous.isBlank()) metadata.put("previousReportId", previous);
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        HttpHeaders xml = new HttpHeaders(); xml.setContentType(MediaType.APPLICATION_XML);
        parts.add("source", new HttpEntity<>(source.getResource(), xml));
        HttpHeaders json = new HttpHeaders(); json.setContentType(MediaType.APPLICATION_JSON);
        parts.add("metadata", new HttpEntity<>(metadata, json));
        ApiExchange exchange = api.call(ApiRequests.of(ClientApiOperation.SUBMIT_REPORTING_TRANSMISSION)
                .path(fields.getFirst("period")).idempotencyKey(key)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.MULTIPART_FORM_DATA_VALUE).body(parts).build());
        ResultModel.apply(model, exchange);
        model.addAttribute("report", ApiViews.reportResult(exchange.body()));
        model.addAttribute("transmissionResult", ApiViews.reportingResult(exchange.body()));
        return reporting(model);
    }

    @GetMapping("/reports/{reportId}")
    public String report(@PathVariable String reportId, Model model) {
        ApiExchange exchange = api.call(ApiRequests.of(ClientApiOperation.GET_REPORT).path(reportId).build());
        ResultModel.apply(model, exchange);
        ApiExchange receipts = api.call(ApiRequests.of(ClientApiOperation.GET_REPORT_RECEIPTS).path(reportId).build());
        ResultModel.apply(model, exchange, receipts);
        model.addAttribute("report", ApiViews.reportResult(exchange.body()));
        model.addAttribute("receiptsExchange", receipts);
        model.addAttribute("receipts", ApiViews.reportReceipts(receipts.body()));
        return "report-detail";
    }

    private static UUID key(MultiValueMap<String, String> fields, Model model) {
        try { return UUID.fromString(Objects.toString(fields.getFirst("idempotencyKey"), "")); }
        catch (IllegalArgumentException invalid) {
            model.addAttribute("formError", "No request was sent. The submission key was invalid; review and submit again.");
            model.addAttribute("idempotencyKey", UUID.randomUUID());
            return null;
        }
    }
}
