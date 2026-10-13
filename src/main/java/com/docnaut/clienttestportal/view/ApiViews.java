package com.docnaut.clienttestportal.view;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads the Client API's responses into the shapes the screens render.
 *
 * <p>This is presentation only. Nothing here decides an outcome, supplies a value the API did not
 * send, or repairs a malformed response: a field the API omitted stays absent, and a body that
 * cannot be read yields nothing at all, leaving the raw response panel as the sole account.
 */
public final class ApiViews {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

    private ApiViews() {
    }

    public record Report(String reportId, String interfaceCode, String kind, String status,
            String periodStart, String periodEnd, String transmittedAt, String transportReference, String createdAt) {}
    public record ReportReceipt(String id, String interfaceCode, String correlationStatus,
            String correlationReason, String businessStatus, String remoteStatusCode,
            String remoteReason, String sha256, String receivedAt) { }
    public static List<ReportReceipt> reportReceipts(String body) {
        if (body == null || body.isBlank()) return null;
        try {
            JsonNode array = MAPPER.readTree(body);
            if (!array.isArray()) return null;
            List<ReportReceipt> result = new ArrayList<>();
            for (JsonNode node : array) {
                if (!node.path("id").isString()) return null;
                result.add(new ReportReceipt(text(node, "id"), text(node, "interfaceCode"),
                        text(node, "correlationStatus"), text(node, "correlationReason"),
                        text(node, "businessStatus"), text(node, "remoteStatusCode"),
                        text(node, "remoteReason"), text(node, "sha256"), stamp(text(node, "receivedAt"))));
            }
            return List.copyOf(result);
        } catch (RuntimeException unreadable) {
            return null;
        }
    }
    public record ReportingResult(String category,String kind,Integer revision,String previousReportId,boolean replayed) {}
    public static ReportingResult reportingResult(String body) {
        return read(body, node -> node.path("report").isObject() && node.path("revision").isIntegralNumber()
                ? new ReportingResult(text(node,"category"),text(node,"kind"),node.path("revision").asInt(),
                        text(node,"previousReportId"),node.path("replayed").asBoolean()) : null);
    }
    public record PaymentResult(String paymentId, String reportId, boolean replayed) {}
    public static Report reportResult(String body) {
        return read(body, value -> {
            var node = value.has("report") ? value.path("report") : value;
            if (!node.path("reportId").isString() || !node.path("status").isString()) return null;
            return new Report(text(node, "reportId"), text(node, "interfaceCode"), text(node, "kind"),
                    text(node, "status"), text(node, "periodStart"), text(node, "periodEnd"),
                    stamp(text(node, "transmittedAt")), text(node, "transportReference"), stamp(text(node, "createdAt")));
        });
    }
    public static PaymentResult paymentResult(String body) {
        return read(body, node -> node.path("paymentId").isString() && node.path("reportId").isString()
                ? new PaymentResult(text(node, "paymentId"), text(node, "reportId"), node.path("replayed").asBoolean()) : null);
    }

    public static Profile profile(String body) {
        return read(body, node -> new Profile(text(node, "serviceClientId"),
                text(node.path("tenant"), "name"), text(node.path("tenant"), "status"),
                strings(node.path("permissions"))));
    }

    public static Configuration configuration(String body) {
        return read(body, node -> new Configuration(text(node, "preferredLocale"),
                text(node, "preferredTimeZone"), node.path("version").asLong()));
    }

    public static Page<Document> documents(String body) {
        return page(body, ApiViews::document);
    }

    /** Intake answers with the document nested beside its replay verdict. */
    public static Document intakeResult(String body) {
        return read(body, node -> document(node.has("document") ? node.path("document") : node));
    }

    public static Document document(String body) {
        return read(body, ApiViews::document);
    }

    public static Page<Event> events(String body) {
        return page(body, node -> new Event(text(node, "eventId"), text(node, "type"),
                stamp(text(node, "occurredAt"))));
    }

    public static Processing processing(String body) {
        return read(body, node -> {
            if (!node.path("status").isString() || !node.path("currentStage").isString()) return null;
            return new Processing(text(node, "status"), text(node, "currentStage"),
                    text(node, "safeErrorCode"), stamp(text(node, "updatedAt")),
                    processingAttempts(node.path("attempts")), externalCalls(node.path("externalCalls")));
        });
    }

    public static InvoiceDecision decision(String body) {
        return read(body, node -> {
            if (!node.path("decision").isString()
                    || !node.path("ppf").isObject()
                    || !node.path("peppol").isObject()) {
                return null;
            }
            return new InvoiceDecision(text(node, "decisionId"), text(node, "documentId"),
                    text(node, "decision"), text(node, "reasonCode"), text(node, "comment"),
                    text(node, "expectedAction"), actionLeg(node.path("ppf")),
                    actionLeg(node.path("peppol")), actionLeg(node.path("chorus")), text(node, "actorServiceClientId"),
                    stamp(text(node, "createdAt")), node.path("sequence").asInt(), decisionHistory(node.path("history")));
        });
    }

    private static List<DecisionHistory> decisionHistory(JsonNode array) {
        List<DecisionHistory> result = new ArrayList<>();
        for (JsonNode node : array) {
            result.add(new DecisionHistory(text(node, "decisionId"), node.path("sequence").asInt(),
                    text(node, "decision"), text(node, "reasonCode"), text(node, "comment"),
                    actionLeg(node.path("chorus")), stamp(text(node, "createdAt")),
                    text(node, "expectedAction"), text(node, "actorServiceClientId")));
        }
        return List.copyOf(result);
    }

    public static ApiProblem problem(String body) {
        return read(body, node -> node.path("status").isIntegralNumber() && node.path("code").isString()
                ? new ApiProblem(node.path("status").asInt(), text(node, "code"), text(node, "correlationId"))
                : null);
    }

    private static ActionLeg actionLeg(JsonNode node) {
        return new ActionLeg(text(node, "status"), text(node, "errorCode"), text(node, "reportId"),
                text(node, "correlationId"));
    }

    private static List<ProcessingAttempt> processingAttempts(JsonNode array) {
        List<ProcessingAttempt> attempts = new ArrayList<>();
        for (JsonNode node : array) {
            attempts.add(new ProcessingAttempt(text(node, "stage"), node.path("attemptNumber").asInt(),
                    text(node, "outcome"), node.path("retryable").asBoolean(),
                    text(node, "safeErrorCode"), stamp(text(node, "completedAt"))));
        }
        return List.copyOf(attempts);
    }

    private static List<ExternalCall> externalCalls(JsonNode array) {
        List<ExternalCall> calls = new ArrayList<>();
        for (JsonNode node : array) {
            calls.add(new ExternalCall(text(node, "system"), text(node, "operation"), text(node, "outcome"),
                    text(node, "requestJson"), text(node, "responseJson"), text(node, "safeErrorCode"),
                    text(node, "correlationId"), stamp(text(node, "occurredAt"))));
        }
        return List.copyOf(calls);
    }

    public static Page<Artifact> artifacts(String body) {
        return page(body, node -> new Artifact(text(node, "artifactId"), text(node, "type"),
                text(node, "mediaType"), node.path("contentLength").asLong(), text(node, "sha256"),
                stamp(text(node, "createdAt"))));
    }

    public static Page<Subscription> subscriptions(String body) {
        return page(body, ApiViews::subscription);
    }

    /** Creation and rotation wrap the subscription beside a secret shown exactly once. */
    public static Subscription subscription(String body) {
        return read(body, node -> subscription(node.has("subscription") ? node.path("subscription") : node));
    }

    public static String issuedSecret(String body) {
        return read(body, node -> text(node, "secret"));
    }

    public static Page<Delivery> deliveries(String body) {
        return page(body, ApiViews::delivery);
    }

    public static Delivery delivery(String body) {
        return read(body, ApiViews::delivery);
    }

    private static Document document(JsonNode node) {
        return new Document(text(node, "documentTraceId"), node.path("documentYear").isIntegralNumber()
                ? node.path("documentYear").asInt() : null,
                text(node, "tenantDocumentNumber"), text(node, "declaredDocumentCategory"),
                text(node, "originalFileName"), text(node, "mediaType"), node.path("contentLength").asLong(),
                text(node, "sha256"), text(node, "intakeStatus"), stamp(text(node, "createdAt")));
    }

    private static Subscription subscription(JsonNode node) {
        JsonNode selection = node.path("eventSelection");
        return new Subscription(text(node, "subscriptionId"), text(node, "name"), text(node, "endpoint"),
                text(selection, "mode"), strings(selection.path("eventTypes")), text(node, "status"),
                node.path("version").asLong(), stamp(text(node, "createdAt")), stamp(text(node, "updatedAt")),
                strings(node.path("customHeaderNames")));
    }

    private static Delivery delivery(JsonNode node) {
        List<Attempt> attempts = new ArrayList<>();
        for (JsonNode attempt : node.path("attempts")) {
            attempts.add(new Attempt(attempt.path("attemptNumber").asInt(), stamp(text(attempt, "startedAt")),
                    stamp(text(attempt, "completedAt")), text(attempt, "outcome"),
                    attempt.path("httpStatus").isIntegralNumber() ? attempt.path("httpStatus").asInt() : null,
                    stamp(text(attempt, "nextAttemptAt"))));
        }
        return new Delivery(text(node, "deliveryId"), text(node, "subscriptionId"), text(node, "eventId"),
                text(node, "eventType"), text(node, "status"), stamp(text(node, "createdAt")), attempts);
    }

    private static <T> Page<T> page(String body, Function<JsonNode, T> item) {
        Page<T> empty = new Page<>(List.of(), null);
        Page<T> read = read(body, node -> {
            if (!node.path("items").isArray()) {
                return null;
            }
            List<T> items = new ArrayList<>();
            for (JsonNode element : node.path("items")) {
                items.add(item.apply(element));
            }
            JsonNode cursor = node.path("nextCursor");
            return new Page<>(items, cursor.isString() && !cursor.stringValue().isBlank()
                    ? cursor.stringValue() : null);
        });
        return read == null ? empty : read;
    }

    private static <T> T read(String body, Function<JsonNode, T> reader) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode node = MAPPER.readTree(body);
            return node.isObject() ? reader.apply(node) : null;
        } catch (RuntimeException unreadable) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isString() && !value.stringValue().isBlank() ? value.stringValue() : null;
    }

    private static List<String> strings(JsonNode array) {
        List<String> values = new ArrayList<>();
        for (JsonNode element : array) {
            if (element.isString()) {
                values.add(element.stringValue());
            }
        }
        return List.copyOf(values);
    }

    /** Timestamps are shown in UTC, the zone the API speaks, so nothing is silently re-interpreted. */
    private static String stamp(String value) {
        if (value == null) {
            return null;
        }
        try {
            return STAMP.format(Instant.parse(value)) + " UTC";
        } catch (RuntimeException notAnInstant) {
            return value;
        }
    }

    public record Page<T>(List<T> items, String nextCursor) {
        public boolean empty() {
            return items.isEmpty();
        }
    }

    public record Profile(String serviceClientId, String tenantName, String tenantStatus,
            List<String> permissions) {
    }

    public record Configuration(String preferredLocale, String preferredTimeZone, long version) {
    }

    public record Document(String documentTraceId, Integer documentYear, String tenantDocumentNumber,
            String declaredDocumentCategory, String originalFileName, String mediaType, long contentLength,
            String sha256, String intakeStatus, String createdAt) {
    }

    public record Event(String eventId, String type, String occurredAt) {
    }

    public record Processing(String status, String currentStage, String safeErrorCode,
            String updatedAt, List<ProcessingAttempt> attempts, List<ExternalCall> externalCalls) {
    }

    public record InvoiceDecision(String decisionId, String documentId, String decision,
            String reasonCode, String comment, String expectedAction, ActionLeg ppf,
            ActionLeg peppol, ActionLeg chorus, String actorServiceClientId, String createdAt,
            int sequence, List<DecisionHistory> history) {
    }

    public record DecisionHistory(String decisionId, int sequence, String decision, String reasonCode,
            String comment, ActionLeg chorus, String createdAt, String expectedAction,
            String actorServiceClientId) { }

    /** One independent leg of a decision; {@code reportId} is set once the PPF leg references an F6 report. */
    public record ActionLeg(String status, String errorCode, String reportId, String correlationId) {
    }

    public record ApiProblem(int status, String code, String correlationId) {
    }

    public record ProcessingAttempt(String stage, int attemptNumber, String outcome,
            boolean retryable, String safeErrorCode, String completedAt) {
    }

    public record ExternalCall(String system, String operation, String outcome, String requestJson,
            String responseJson, String safeErrorCode, String correlationId, String occurredAt) { }

    public record Artifact(String artifactId, String type, String mediaType, long contentLength,
            String sha256, String createdAt) {
    }

    public record Subscription(String subscriptionId, String name, String endpoint, String mode,
            List<String> eventTypes, String status, long version, String createdAt, String updatedAt,
            List<String> customHeaderNames) {
    }

    public record Delivery(String deliveryId, String subscriptionId, String eventId, String eventType,
            String status, String createdAt, List<Attempt> attempts) {
    }

    public record Attempt(int attemptNumber, String startedAt, String completedAt, String outcome,
            Integer httpStatus, String nextAttemptAt) {
    }
}
