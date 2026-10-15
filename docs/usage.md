# Using the tool

1. Open the login page and select Sandbox or Production.
2. Enter credentials for an existing service client.
3. Choose a workflow from the sidebar. The environment remains associated with the session; sign out to change it.

The Production banner identifies requests that can affect real data. Client permissions and the remote API determine whether an operation is allowed.

## Documents and payments

Upload a source document through Documents. Preserve its returned trace identifier when inspecting processing, events, artifacts or subsequent actions. An intake success is not a delivery receipt. Inspect processing and event responses for the current result.

Artifact downloads are requested through the API and streamed by the application. Inbound decisions use the selected document's identifier.

For a payment or disbursement, use **Send a document** to upload the original CDAR XML with status 212. The multipart `source` part is sent through `POST /client/api/v1/documents` with an idempotency key. The connector extracts payment facts and the invoice reference from the file; the tool does not synthesize a CDAR or send a payment JSON request.

After processing, open the source CDAR or invoice and inspect **CDAR lifecycle history** (`GET /client/api/v1/documents/{documentTraceId}/lifecycle-events`). Each retained event shows its signed payment facts and independent channel outcomes. A 212 can represent a partial collection or disbursement, so do not infer full payment from one event. UPLOADED/SENT is a transport result, not business acceptance. Open or download the original CDAR using the source document and artifact identifiers shown by the API.

## Reporting, webhooks and settings

Reporting lets you submit a transmission and inspect a report and its receipts. A successful submission is separate from acceptance by the recipient.

Webhook screens manage subscriptions and show delivery records. You must operate the receiving endpoint yourself; this application is not a webhook receiver. Verification and secret rotation perform real API requests.

Configuration replacement uses the API's ETag through `If-Match`. Mutation forms expose idempotency keys where required. Retain the same key when replaying the same request, and use a new key for a different operation. A secret-rotation response may reveal a new secret: handle it privately and update dependent clients.

## Interpreting results

The API-call details show the requests and responses behind a screen. Remote validation, permission and conflict responses remain API outcomes. A login failure concerns credentials or the token endpoint; a later API denial can concern permissions or the chosen environment.

This tool does not independently perform directory lookup, invoice routing or government/network transport. Its screens report the Client API's observations. Local automated tests use controlled remote endpoints and do not prove live external delivery.
