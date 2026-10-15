package com.docnaut.clienttestportal.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.docnaut.clienttestportal.api.ApiExchange;
import com.docnaut.clienttestportal.api.ApiRequests;
import com.docnaut.clienttestportal.api.ClientApiGateway;
import com.docnaut.clienttestportal.api.ClientApiOperation;
import com.docnaut.clienttestportal.view.ApiViews;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriUtils;

@Controller
public class DocumentController {

    /** A page size that fills a screen without asking the operator to choose one. */
    private static final int PAGE = 25;

    private final ClientApiGateway api;

    public DocumentController(ClientApiGateway api) {
        this.api = api;
    }

    @GetMapping("/documents")
    public String list(@RequestParam(name = "cursor", required = false) String cursor, Model model) {
        ApiExchange exchange = api.call(ApiRequests.of(ClientApiOperation.LIST_DOCUMENTS)
                .page(cursor, PAGE).build());
        ResultModel.apply(model, exchange);
        model.addAttribute("documents", ApiViews.documents(exchange.body()));
        return "documents";
    }

    @GetMapping("/documents/send")
    public String sendForm(Model model) {
        model.addAttribute("idempotencyKey", UUID.randomUUID());
        return "document-send";
    }

    /** Lets a plain form open a document by trace id without any client-side scripting. */
    @GetMapping("/documents/open")
    public String open(@RequestParam("documentTraceId") String documentTraceId) {
        return "redirect:/documents/" + UriUtils.encodePathSegment(documentTraceId.trim(), StandardCharsets.UTF_8);
    }

    /**
     * One screen for one document: what it is, what happened to it, and what can be downloaded.
     * The three reads are separate operations with separate permissions, so each is reported
     * separately rather than collapsing into a single success or failure.
     */
    @GetMapping("/documents/{documentTraceId}")
    public String detail(@PathVariable("documentTraceId") String documentTraceId, Model model) {
        return detail(documentTraceId, null, null, model);
    }

    @PostMapping("/documents/{documentTraceId}/decision")
    public String decide(@PathVariable("documentTraceId") String documentTraceId,
            @RequestParam(name = "decision", required = false) String decision,
            @RequestParam(name = "reasonCode", required = false) String reasonCode,
            @RequestParam(name = "comment", required = false) String comment,
            @RequestParam(name = "expectedAction", required = false) String expectedAction,
            @RequestParam(name = "idempotencyKey", required = false) String idempotencyKey,
            Model model) {
        DecisionForm form = new DecisionForm(decision, reasonCode, comment, expectedAction, idempotencyKey);
        UUID key;
        try {
            key = UUID.fromString(idempotencyKey == null ? "" : idempotencyKey);
        } catch (IllegalArgumentException invalidKey) {
            model.addAttribute("decisionNoticeTitle", "Decision was not sent");
            model.addAttribute("decisionNoticeMessage",
                    "The submission key was invalid. A new key has been prepared; review and submit again.");
            model.addAttribute("decisionNoticeGood", false);
            form = form.withIdempotencyKey(UUID.randomUUID().toString());
            return detail(documentTraceId, null, form, model);
        }

        Map<String, Object> body = decisionBody(decision, reasonCode, comment, expectedAction);
        ApiExchange submission = api.call(ApiRequests.of(ClientApiOperation.DECIDE_INBOUND_INVOICE)
                .path(documentTraceId).idempotencyKey(key).body(body).build());
        addDecisionNotice(model, submission);
        return detail(documentTraceId, submission, form, model);
    }

    private String detail(String documentTraceId, ApiExchange submission, DecisionForm submittedForm, Model model) {
        ApiExchange document = api.call(ApiRequests.of(ClientApiOperation.GET_DOCUMENT)
                .path(documentTraceId).build());
        ApiExchange decision = api.call(ApiRequests.of(ClientApiOperation.GET_INBOUND_INVOICE_DECISION)
                .path(documentTraceId).build());
        ApiExchange events = api.call(ApiRequests.of(ClientApiOperation.LIST_DOCUMENT_EVENTS)
                .path(documentTraceId).page(null, PAGE).build());
        ApiExchange processing = api.call(ApiRequests.of(ClientApiOperation.GET_DOCUMENT_PROCESSING)
                .path(documentTraceId).build());
        ApiExchange artifacts = api.call(ApiRequests.of(ClientApiOperation.LIST_DOCUMENT_ARTIFACTS)
                .path(documentTraceId).page(null, PAGE).build());
        ApiExchange lifecycle = api.call(ApiRequests.of(ClientApiOperation.LIST_DOCUMENT_LIFECYCLE_EVENTS)
                .path(documentTraceId).build());
        ResultModel.apply(model, submission, document, decision, processing, events, artifacts, lifecycle);
        model.addAttribute("lifecycle", ApiViews.lifecycleEvents(lifecycle.body()));
        model.addAttribute("lifecycleExchange", lifecycle);
        model.addAttribute("documentTraceId", documentTraceId);
        model.addAttribute("document", ApiViews.document(document.body()));
        model.addAttribute("documentExchange", document);
        ApiViews.InvoiceDecision decisionView = ApiViews.decision(decision.body());
        model.addAttribute("decision", decisionView);
        model.addAttribute("decisionExchange", decision);
        boolean decisionFormEnabled = (decisionView == null && decision.status() == 404)
                || (decisionView != null && "SUSPENDED".equals(decisionView.decision()));
        model.addAttribute("decisionFormEnabled", decisionFormEnabled);
        model.addAttribute("decisionForm", decisionFormEnabled
                ? (submittedForm == null || (submission != null && submission.successful())
                        ? DecisionForm.empty() : submittedForm)
                : DecisionForm.from(decisionView));
        model.addAttribute("events", ApiViews.events(events.body()));
        model.addAttribute("eventsExchange", events);
        model.addAttribute("processing", ApiViews.processing(processing.body()));
        model.addAttribute("processingExchange", processing);
        model.addAttribute("artifacts", ApiViews.artifacts(artifacts.body()));
        model.addAttribute("artifactsExchange", artifacts);
        return "document-detail";
    }

    private static Map<String, Object> decisionBody(String decision, String reasonCode,
            String comment, String expectedAction) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("decision", blankToNull(decision));
        if ("REFUSED".equals(decision) || "SUSPENDED".equals(decision)) {
            body.put("reasonCode", blankToNull(reasonCode));
            body.put("comment", blankToNull(comment));
            if ("REFUSED".equals(decision) && blankToNull(expectedAction) != null) {
                body.put("expectedAction", expectedAction);
            }
        }
        return body;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static void addDecisionNotice(Model model, ApiExchange submission) {
        model.addAttribute("decisionNoticeGood", submission.successful());
        if (submission.successful()) {
            model.addAttribute("decisionNoticeTitle", "Decision accepted or replayed");
            model.addAttribute("decisionNoticeMessage",
                    "The API returned the immutable action. Its PPF, Peppol and Chorus states are shown separately below.");
            return;
        }
        ApiViews.ApiProblem problem = ApiViews.problem(submission.body());
        model.addAttribute("decisionNoticeCode", problem == null ? null : problem.code());
        switch (submission.status()) {
            case 400 -> {
                model.addAttribute("decisionNoticeTitle", "Check the decision fields");
                model.addAttribute("decisionNoticeMessage",
                        "Refusal or suspension needs a valid reason code and comment. Suspension is Chorus-only and does not accept an expected action.");
            }
            case 409 -> {
                model.addAttribute("decisionNoticeTitle", "Decision conflict");
                model.addAttribute("decisionNoticeMessage",
                        "A final decision already exists, the previous Chorus lifecycle delivery is pending or rejected, or this submission key was reused for different values.");
            }
            default -> {
                model.addAttribute("decisionNoticeTitle", "Decision was not accepted");
                model.addAttribute("decisionNoticeMessage",
                        "Open the API calls below for the status, safe problem code and correlation id.");
            }
        }
    }

    public record DecisionForm(String decision, String reasonCode, String comment,
            String expectedAction, String idempotencyKey) {
        static DecisionForm empty() {
            return new DecisionForm(null, null, null, null, UUID.randomUUID().toString());
        }

        static DecisionForm from(ApiViews.InvoiceDecision decision) {
            return new DecisionForm(decision.decision(), decision.reasonCode(), decision.comment(),
                    decision.expectedAction(), null);
        }

        DecisionForm withIdempotencyKey(String replacement) {
            return new DecisionForm(decision, reasonCode, comment, expectedAction, replacement);
        }
    }

    /** Intake streams the uploaded part straight to the API; the body is never held in memory. */
    @PostMapping("/documents")
    public String send(@RequestParam("source") MultipartFile source,
            @RequestParam("idempotencyKey") UUID idempotencyKey, Model model) throws IOException {
        model.addAttribute("idempotencyKey", idempotencyKey);
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.parseMediaType(source.getContentType() == null
                ? MediaType.APPLICATION_OCTET_STREAM_VALUE : source.getContentType()));

        // The parts are assembled by hand rather than with MultipartBodyBuilder, whose signatures
        // reference Reactive Streams: this is a servlet application and that library is not on the
        // classpath, so touching the builder fails at class-loading time.
        MultiValueMap<String, HttpEntity<?>> parts = new LinkedMultiValueMap<>();
        parts.add("source", new HttpEntity<>(new InputStreamResource(source.getInputStream()) {
            @Override
            public String getFilename() {
                return source.getOriginalFilename();
            }

            @Override
            public long contentLength() {
                return source.getSize();
            }
        }, fileHeaders));

        ApiExchange exchange = api.call(ApiRequests.of(ClientApiOperation.CREATE_DOCUMENT_INTAKE)
                .idempotencyKey(idempotencyKey)
                .header("Content-Type", MediaType.MULTIPART_FORM_DATA_VALUE)
                .body(parts)
                .build());
        ResultModel.apply(model, exchange);
        model.addAttribute("accepted", ApiViews.intakeResult(exchange.body()));
        return "document-send";
    }

    /**
     * The artefact is a controlled stream: the API exposes no storage locator, so the operator
     * chooses a row rather than quoting an identifier the portal would have to hold.
     */
    @PostMapping("/documents/{documentTraceId}/download")
    public String download(@PathVariable("documentTraceId") String documentTraceId,
            @RequestParam("artifactId") String artifactId, HttpServletResponse response, Model model) {
        ApiExchange exchange = api.streamArtifact(ApiRequests.of(ClientApiOperation.CREATE_ARTIFACT_ACCESS)
                .path(documentTraceId, artifactId).build(), response);
        if (exchange.successful()) {
            return null;
        }
        String view = detail(documentTraceId, model);
        model.addAttribute("downloadFailure", exchange);
        return view;
    }

}
