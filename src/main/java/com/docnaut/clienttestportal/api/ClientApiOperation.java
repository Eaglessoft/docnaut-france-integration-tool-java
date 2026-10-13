package com.docnaut.clienttestportal.api;

import org.springframework.http.HttpMethod;

/**
 * Every operation the portal can reach. The portal is not an arbitrary method/path proxy:
 * a request that is not one of these entries cannot be constructed.
 *
 * <p>Mirrors the public Client API contract. Method,
 * path and permission are contract facts; the portal adds no interpretation of what the API
 * decides once the request arrives.
 */
public enum ClientApiOperation {
    GET_CLIENT_PROFILE("getClientProfile", HttpMethod.GET, "/profile", "client.profile.read"),
    GET_CLIENT_CONFIGURATION("getClientConfiguration", HttpMethod.GET, "/configuration",
            "client.configuration.read"),
    REPLACE_CLIENT_CONFIGURATION("replaceClientConfiguration", HttpMethod.PUT, "/configuration",
            "client.configuration.manage"),
    ROTATE_OWN_SERVICE_CLIENT_SECRET("rotateOwnServiceClientSecret", HttpMethod.POST,
            "/service-client/rotate-secret", "client.service-client.rotate-secret"),
    CREATE_DOCUMENT_INTAKE("createDocumentIntake", HttpMethod.POST, "/documents", "client.document.submit"),
    REGISTER_INVOICE_PAYMENT("registerInvoicePayment", HttpMethod.POST,
            "/documents/{documentId}/payments", "client.document.submit"),
    SUBMIT_REPORTING_TRANSMISSION("submitReportingTransmission", HttpMethod.POST,
            "/reporting-periods/{period}/transmissions", "client.document.submit"),
    GET_REPORT_RECEIPTS("getReportReceipts", HttpMethod.GET, "/reports/{reportId}/receipts", "client.document.read"),
    GET_REPORT("getReport", HttpMethod.GET, "/reports/{reportId}", "client.document.read"),
    LIST_PEPPOL_FACTS("listPeppolFacts", HttpMethod.GET, "/peppol/facts", "client.document.read"),
    LIST_DOCUMENTS("listDocuments", HttpMethod.GET, "/documents", "client.document.read"),
    GET_DOCUMENT("getDocument", HttpMethod.GET, "/documents/{documentTraceId}", "client.document.read"),
    GET_DOCUMENT_PROCESSING("getDocumentProcessing", HttpMethod.GET,
            "/documents/{documentTraceId}/processing", "client.document.read"),
    DECIDE_INBOUND_INVOICE("decideInboundInvoice", HttpMethod.POST,
            "/documents/{documentId}/decision", "client.document.decision.manage"),
    GET_INBOUND_INVOICE_DECISION("getInboundInvoiceDecision", HttpMethod.GET,
            "/documents/{documentId}/decision", "client.document.decision.manage"),
    LIST_DOCUMENT_EVENTS("listDocumentEvents", HttpMethod.GET, "/documents/{documentTraceId}/events",
            "client.document.event.read"),
    LIST_DOCUMENT_ARTIFACTS("listDocumentArtifacts", HttpMethod.GET, "/documents/{documentTraceId}/artifacts",
            "client.document.artifact.read"),
    CREATE_ARTIFACT_ACCESS("createArtifactAccess", HttpMethod.POST,
            "/documents/{documentTraceId}/artifacts/{artifactId}/access", "client.document.artifact.download"),
    LIST_CLIENT_WEBHOOK_SUBSCRIPTIONS("listClientWebhookSubscriptions", HttpMethod.GET, "/webhook-subscriptions",
            "client.webhook.read"),
    CREATE_CLIENT_WEBHOOK_SUBSCRIPTION("createClientWebhookSubscription", HttpMethod.POST, "/webhook-subscriptions",
            "client.webhook.manage"),
    GET_CLIENT_WEBHOOK_SUBSCRIPTION("getClientWebhookSubscription", HttpMethod.GET,
            "/webhook-subscriptions/{subscriptionId}", "client.webhook.read"),
    UPDATE_CLIENT_WEBHOOK_SUBSCRIPTION("updateClientWebhookSubscription", HttpMethod.PATCH,
            "/webhook-subscriptions/{subscriptionId}", "client.webhook.manage"),
    VERIFY_CLIENT_WEBHOOK_SUBSCRIPTION("verifyClientWebhookSubscription", HttpMethod.POST,
            "/webhook-subscriptions/{subscriptionId}/verify", "client.webhook.manage"),
    ROTATE_CLIENT_WEBHOOK_SECRET("rotateClientWebhookSecret", HttpMethod.POST,
            "/webhook-subscriptions/{subscriptionId}/rotate-secret", "client.webhook.manage"),
    LIST_CLIENT_WEBHOOK_DELIVERIES("listClientWebhookDeliveries", HttpMethod.GET, "/webhook-deliveries",
            "client.webhook.delivery.read"),
    GET_CLIENT_WEBHOOK_DELIVERY("getClientWebhookDelivery", HttpMethod.GET, "/webhook-deliveries/{deliveryId}",
            "client.webhook.delivery.read");

    private final String operationId;
    private final HttpMethod method;
    private final String pathTemplate;
    private final String permission;

    ClientApiOperation(String operationId, HttpMethod method, String pathTemplate, String permission) {
        this.operationId = operationId;
        this.method = method;
        this.pathTemplate = pathTemplate;
        this.permission = permission;
    }

    public String operationId() {
        return operationId;
    }

    public HttpMethod method() {
        return method;
    }

    public String pathTemplate() {
        return pathTemplate;
    }

    public String permission() {
        return permission;
    }
}
