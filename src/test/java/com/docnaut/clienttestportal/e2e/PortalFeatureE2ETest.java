package com.docnaut.clienttestportal.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.docnaut.clienttestportal.PortalApplication;
import com.sun.net.httpserver.HttpServer;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;

/**
 * Portal-application E2E: real listener, security, templates and outbound HTTP clients.
 * Only the external OIDC token endpoint and Connector are controlled HTTP partners.
 * This does not claim full-stack Connector processing or browser rendering acceptance.
 */
class PortalFeatureE2ETest {
    private static final String TRACE = "22222222-2222-2222-2222-222222222222";
    private static final String INVOICE = "<Invoice><ID>PORTAL-E2E-1</ID></Invoice>";
    private static final String DOCUMENT = """
            {"documentTraceId":"22222222-2222-2222-2222-222222222222",
             "documentYear":2026,"tenantDocumentNumber":"PORTAL-E2E-1",
             "declaredDocumentCategory":"INVOICE","originalFileName":"invoice.xml",
             "mediaType":"application/xml","contentLength":46,"intakeStatus":"ACCEPTED",
             "createdAt":"2026-10-12T10:00:00Z"}
            """;

    @Test
    void operatorSignsInSubmitsInvoiceReadsItBackAndSignsOut() throws Exception {
        boolean browser = Boolean.getBoolean("portal.browser");
        AtomicReference<String> productionPath = new AtomicReference<>();
        AtomicReference<String> tokenPath = new AtomicReference<>();
        AtomicReference<String> tokenRequest = new AtomicReference<>();
        AtomicReference<String> upload = new AtomicReference<>();
        AtomicReference<String> uploadKey = new AtomicReference<>();
        AtomicReference<String> uploadContentType = new AtomicReference<>();
        AtomicReference<String> lifecycleBody = new AtomicReference<>("""
                {"items":[{"eventId":"44444444-4444-4444-4444-444444444444","invoiceDocumentTraceId":"22222222-2222-2222-2222-222222222222","sourceDocumentTraceId":"22222222-2222-2222-2222-222222222222",
                "sourceArtifactId":"33333333-3333-3333-3333-333333333333","direction":"CUSTOMER",
                "statusCode":"212","occurredAt":"2026-10-15T10:00:00Z","payment":{"date":"2026-10-15","amount":-25.50,
                "currency":"EUR","cancellationReason":"Partial reversal","allocations":[{"rate":20,"amount":-25.50}]},
                "channels":[{"channel":"PPF","status":"RETRYABLE","errorCode":"TEMPORARY"},
                {"channel":"CHORUS","status":"UPLOADED"}]}]}
                """);
        AtomicReference<Integer> lifecycleStatus = new AtomicReference<>(200);
        AtomicReference<String> lifecycleQuery = new AtomicReference<>();
        AtomicReference<String> unexpectedRequest = new AtomicReference<>();
        HttpServer partner = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        partner.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.startsWith("/production/")) { productionPath.set(path); path = path.substring("/production".length()); }
            String method = exchange.getRequestMethod();
            String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            int status = 200;
            String body;
            if (path.equals("/token") && method.equals("POST")) {
                tokenPath.set(exchange.getRequestURI().getPath());
                tokenRequest.set(request);
                body = "{\"access_token\":\"portal-e2e-token\",\"expires_in\":300}";
            } else if (!"Bearer portal-e2e-token".equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
                status = 401;
                body = "{}";
            } else if (path.equals("/client/api/v1/profile") && method.equals("GET")) {
                body = "{\"serviceClientId\":\"operator\",\"tenant\":{\"name\":\"Browser acceptance fixture\",\"status\":\"ACTIVE\"},\"permissions\":[\"client.document.read\",\"client.document.submit\"]}";
            } else if (path.equals("/client/api/v1/webhook-subscriptions") && method.equals("GET")) {
                body = "{\"items\":[],\"nextCursor\":null}";
            } else if (path.equals("/client/api/v1/peppol/facts") && method.equals("GET")) {
                body = "{\"items\":[],\"nextCursor\":null}";
            } else if (path.equals("/client/api/v1/documents") && method.equals("POST")) {
                upload.set(request);
                uploadKey.set(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
                uploadContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
                status = 201;
                body = DOCUMENT;
            } else if (path.equals("/client/api/v1/documents") && method.equals("GET")) {
                body = "{\"items\":[" + (upload.get() == null ? "" : DOCUMENT) + "],\"nextCursor\":null}";
            } else if (path.equals("/client/api/v1/documents/" + TRACE + "/lifecycle-events") && method.equals("GET")) {
                lifecycleQuery.set(exchange.getRequestURI().getRawQuery());
                status = lifecycleStatus.get();
                body = lifecycleBody.get();
            } else if (path.equals("/client/api/v1/documents/" + TRACE) && method.equals("GET")) {
                body = DOCUMENT;
            } else if (path.equals("/client/api/v1/documents/" + TRACE + "/decision") && method.equals("GET")) {
                status = 404; body = "{}";
            } else if ((path.equals("/client/api/v1/documents/" + TRACE + "/events")
                    || path.equals("/client/api/v1/documents/" + TRACE + "/artifacts")) && method.equals("GET")) {
                body = "{\"items\":[]}";
            } else if (path.equals("/client/api/v1/documents/" + TRACE + "/processing") && method.equals("GET")) {
                status = 404; body = "{}";
            } else {
                unexpectedRequest.set(method + " " + path);
                status = 404;
                body = "{}";
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        partner.start();
        String remote = "http://127.0.0.1:" + partner.getAddress().getPort();
        CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient http = HttpClient.newBuilder().cookieHandler(cookies)
                .followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(5)).build();
        try (var context = new SpringApplicationBuilder(PortalApplication.class).run(
                "--server.port=" + (browser ? 8083 : 0), "--server.address=" + (browser ? "0.0.0.0" : "127.0.0.1"), "--server.servlet.context-path=/client-test",
                "--portal.sandbox.api-base-url=" + remote + "/client/api/v1/", "--portal.sandbox.token-url=" + remote + "/token",
                "--portal.sandbox.allow-insecure-http=true",
                "--portal.production.api-base-url=" + remote + "/production/client/api/v1/",
                "--portal.production.token-url=" + remote + "/production/token",
                "--portal.production.allow-insecure-http=true")) {
            String base = "http://127.0.0.1:"
                    + ((ServletWebServerApplicationContext) context).getWebServer().getPort() + "/client-test";
            if (browser) {
                System.out.println("Browser fixture ready: http://localhost:8083/client-test/login ; Sandbox or Production ; operator / fixture");
                System.out.println("Controlled partners only. Stop this process when browser acceptance ends; this mode does not run test assertions.");
                new java.util.concurrent.CountDownLatch(1).await();
            }
            var denied = get(http, base + "/documents");
            assertThat(denied.statusCode()).isEqualTo(302);
            assertThat(denied.headers().firstValue("Location").orElseThrow()).endsWith("/client-test/login");
            var login = get(http, base + "/login");
            assertThat(login.statusCode()).isEqualTo(200);
            assertThat(login.body()).contains("Sandbox", "Production", "Docnaut", "docnaut-logo.webp");
            assertThat(get(http, base + "/assets/docnaut-logo.webp").statusCode()).isEqualTo(200);
            assertThat(login.body()).contains("action=\"/client-test/login\"");
            assertThat(get(http, base + "/assets/portal.css").statusCode()).isEqualTo(200);
            assertThat(post(http, base + "/login", "environment=Sandbox&clientId=operator&clientSecret=fixture").statusCode()).isEqualTo(403);
            assertThat(tokenRequest.get()).isNull();
            assertThat(post(http, base + "/login", "environment=Sandbox&clientId=operator&clientSecret=fixture&_csrf="
                    + field(login.body(), "_csrf")).statusCode()).isEqualTo(302);
            assertThat(tokenPath.get()).isEqualTo("/token");
            assertThat(tokenRequest.get()).contains("grant_type=client_credentials", "client_id=operator", "client_secret=fixture");
            assertThat(get(http, base + "/documents").body()).contains("No documents yet");
            assertThat(get(http, base + "/peppol-facts?limit=2").body()).contains("Peppol facts", "listPeppolFacts");
            var form = get(http, base + "/documents/send");
            assertThat(form.statusCode()).isEqualTo(200);
            String key = field(form.body(), "idempotencyKey");
            assertThat(UUID.fromString(key)).isNotNull();
            String multipart = "--portal-boundary\r\nContent-Disposition: form-data; name=\"idempotencyKey\"\r\n\r\n"
                    + key + "\r\n--portal-boundary\r\nContent-Disposition: form-data; name=\"source\"; filename=\"invoice.xml\""
                    + "\r\nContent-Type: application/xml\r\n\r\n" + INVOICE + "\r\n--portal-boundary--\r\n";
            var forbiddenUpload = sendMultipart(http, base + "/documents", multipart);
            assertThat(forbiddenUpload.statusCode()).isEqualTo(403);
            assertThat(upload.get()).isNull();
            var accepted = sendMultipart(http, base + "/documents?_csrf=" + field(form.body(), "_csrf"), multipart);
            assertThat(accepted.statusCode()).isEqualTo(200);
            assertThat(accepted.body()).contains("Accepted", TRACE, "/client-test/documents/" + TRACE);
            assertThat(uploadKey.get()).isEqualTo(key);
            assertThat(uploadContentType.get()).startsWith("multipart/form-data;");
            assertThat(upload.get()).contains("name=\"source\"", "filename=\"invoice.xml\"", "application/xml", INVOICE);
            var documents = get(http, base + "/documents");
            assertThat(documents.statusCode()).isEqualTo(200);
            assertThat(documents.body()).contains("PORTAL-E2E-1", "invoice.xml", TRACE).doesNotContain("No documents yet");
            // The portal forwards the caller's original CDAR bytes, never a payment JSON projection.
            String cdar = new String(getClass().getResourceAsStream("/fixtures/cdar-212.xml").readAllBytes(), StandardCharsets.UTF_8);
            String paymentUpload = multipart.replace("invoice.xml", "payment-212.xml").replace(INVOICE, cdar);
            assertThat(sendMultipart(http, base + "/documents?_csrf=" + field(form.body(), "_csrf"), paymentUpload).statusCode()).isEqualTo(200);
            assertThat(upload.get()).contains("filename=\"payment-212.xml\"", cdar);
            var detail = get(http, base + "/documents/" + TRACE);
            assertThat(detail.statusCode()).isEqualTo(200);
            assertThat(detail.body()).contains("CDAR lifecycle history", "212 — CUSTOMER", "-25.50", "Partial reversal",
                    "RETRYABLE", "TEMPORARY", "UPLOADED", "Download original CDAR", "Upload payment CDAR (212)")
                    .doesNotContain("Register payment", "/payment\"");
            assertThat(lifecycleQuery.get()).isNull();
            lifecycleBody.set("{\"items\":[]}");
            assertThat(get(http, base + "/documents/" + TRACE).body()).contains("No lifecycle events recorded.");
            lifecycleStatus.set(403);
            assertThat(get(http, base + "/documents/" + TRACE).body()).contains("The API returned 403 for the lifecycle history").doesNotContain("No lifecycle events recorded.");
            lifecycleStatus.set(200);
            assertThat(post(http, base + "/logout", "").statusCode()).isEqualTo(403);
            assertThat(post(http, base + "/logout", "_csrf=" + field(documents.body(), "_csrf")).statusCode()).isEqualTo(302);
            assertThat(get(http, base + "/documents").statusCode()).isEqualTo(302);
            var secondLogin = get(http, base + "/login");
            tokenRequest.set(null);
            var invalidEnvironment = post(http, base + "/login", "environment=Unknown&clientId=operator&clientSecret=fixture&_csrf=" + field(secondLogin.body(), "_csrf"));
            assertThat(invalidEnvironment.headers().firstValue("Location").orElseThrow()).endsWith("/login?failed");
            assertThat(tokenRequest.get()).isNull();
            secondLogin = get(http, base + "/login");
            assertThat(post(http, base + "/login", "environment=Production&clientId=operator&clientSecret=fixture&_csrf=" + field(secondLogin.body(), "_csrf")).statusCode()).isEqualTo(302);
            assertThat(tokenPath.get()).isEqualTo("/production/token");
            var productionDocuments = get(http, base + "/documents");
            assertThat(productionDocuments.statusCode()).isEqualTo(200);
            assertThat(productionDocuments.body()).contains("Production — requests can change real documents and settings.", "PORTAL-E2E-1")
                    .doesNotContain("portal-e2e-token", "client_secret=fixture");
            assertThat(productionPath.get()).isEqualTo("/production/client/api/v1/documents");
            assertThat(post(http, base + "/logout", "_csrf=" + field(productionDocuments.body(), "_csrf")).statusCode()).isEqualTo(302);
            assertThat(unexpectedRequest.get()).isNull();
        } finally {
            partner.stop(0);
        }
    }

    private static String field(String html, String name) {
        var match = Pattern.compile("name=\"" + Pattern.quote(name) + "\"[^>]*value=\"([^\"]+)\"").matcher(html);
        assertThat(match.find()).as("rendered field %s", name).isTrue();
        return URLEncoder.encode(match.group(1), StandardCharsets.UTF_8);
    }

    private static HttpResponse<String> get(HttpClient http, String uri) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(uri)).timeout(Duration.ofSeconds(10)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> post(HttpClient http, String uri, String body) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(uri)).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> sendMultipart(HttpClient http, String uri, String body) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(uri)).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "multipart/form-data; boundary=portal-boundary")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
}
