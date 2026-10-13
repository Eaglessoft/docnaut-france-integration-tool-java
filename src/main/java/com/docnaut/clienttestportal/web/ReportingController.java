package com.docnaut.clienttestportal.web;

import com.docnaut.clienttestportal.api.*;
import com.docnaut.clienttestportal.view.ApiViews;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
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

    @GetMapping("/documents/{documentId}/payment")
    public String payment(@PathVariable String documentId, Model model) {
        model.addAttribute("documentId", documentId);
        if (!model.containsAttribute("fields")) model.addAttribute("fields", Map.of());
        if (!model.containsAttribute("idempotencyKey")) model.addAttribute("idempotencyKey", UUID.randomUUID());
        return "payment";
    }

    @PostMapping("/documents/{documentId}/payment")
    public String pay(@PathVariable String documentId, @RequestParam MultiValueMap<String, String> fields, Model model) {
        model.addAttribute("fields", fields.toSingleValueMap());
        model.addAttribute("idempotencyKey", fields.getFirst("idempotencyKey"));
        UUID key = key(fields, model);
        if (key == null) return payment(documentId, model);
        Map<String, Object> body;
        try { body = paymentBody(fields); }
        catch (IllegalArgumentException | java.time.DateTimeException invalid) {
            model.addAttribute("formError", "Enter valid decimal amounts and an occurrence time with UTC offset.");
            return payment(documentId, model);
        }
        ApiExchange exchange = api.call(ApiRequests.of(ClientApiOperation.REGISTER_INVOICE_PAYMENT)
                .path(documentId).idempotencyKey(key).body(body).build());
        ResultModel.apply(model, exchange);
        model.addAttribute("payment", ApiViews.paymentResult(exchange.body()));
        return payment(documentId, model);
    }

    static Map<String, Object> paymentBody(MultiValueMap<String, String> fields) {
        for (String name : List.of("amount", "currency", "paymentReference", "occurredAt")) {
            if (fields.getFirst(name) == null || fields.getFirst(name).isBlank()) throw new IllegalArgumentException("Missing field");
        }
        Map<String, Object> payment = new LinkedHashMap<>();
        payment.put("amount", new BigDecimal(fields.getFirst("amount")));
        payment.put("currency", fields.getFirst("currency"));
        List<Map<String, BigDecimal>> allocation = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            String rate = fields.getFirst("rate" + i), amount = fields.getFirst("vatAmount" + i);
            if ((rate == null || rate.isBlank()) && (amount == null || amount.isBlank())) continue;
            allocation.add(Map.of("rate", new BigDecimal(rate == null ? "" : rate),
                    "amount", new BigDecimal(amount == null ? "" : amount)));
        }
        payment.put("vatBreakdown", allocation);
        for (String name : List.of("remainingAmount", "discountAmount")) {
            String value = fields.getFirst(name);
            if (value != null && !value.isBlank()) payment.put(name, new BigDecimal(value));
        }
        String cancellation = fields.getFirst("cancellationReason");
        if (cancellation != null && !cancellation.isBlank()) payment.put("cancellationReason", cancellation);
        return Map.of("paymentReference", fields.getFirst("paymentReference"),
                "occurredAt", Instant.parse(fields.getFirst("occurredAt")).toString(), "payment", payment);
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
