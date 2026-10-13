# Configuration

Set environment variables before starting the application. Login accepts an environment and service-client credentials; endpoint addresses are controlled by the operator.

| Variable | Default |
|---|---|
| `DOCNAUT_SANDBOX_API_BASE_URL` | `https://france-staging.docnaut.com/client/api/v1/` |
| `DOCNAUT_SANDBOX_TOKEN_URL` | `https://france.docnaut.com/sso/realms/docnaut/protocol/openid-connect/token` |
| `DOCNAUT_SANDBOX_ALLOW_INSECURE_HTTP` | `false` |
| `DOCNAUT_PRODUCTION_API_BASE_URL` | `https://france.docnaut.com/client/api/v1/` |
| `DOCNAUT_PRODUCTION_TOKEN_URL` | `https://france.docnaut.com/sso/realms/docnaut/protocol/openid-connect/token` |
| `DOCNAUT_PRODUCTION_ALLOW_INSECURE_HTTP` | `false` |
| `SERVER_PORT` | `8094` outside the image; image uses `8080` |

An API base URL must end with `/client/api/v1/`. Both endpoints must be absolute HTTPS URLs without embedded credentials, query strings or fragments. The insecure flag permits HTTP only for localhost or loopback addresses; it does not permit arbitrary container hostnames. Inside a container, localhost refers to that container.

Both default environments share the configured identity endpoint. Successful OAuth login confirms token acquisition, not permission for every API operation or delivery to an external network.

## Sessions and hosting

Credentials are entered on the login page, not stored in repository configuration. The server retains the client secret and access token in an in-memory session for token renewal. The default idle timeout is 30 minutes. Logout and process replacement require a new login. There is no database or shared session store.

The provided Compose mapping binds to loopback. Before exposing the app to other users, provide HTTPS and set `SERVER_SERVLET_SESSION_COOKIE_SECURE=true`. Configure forwarding headers only for a trusted reverse proxy. The app does not provision TLS, an ingress controller or an identity account.

Uploads are limited to 100 MB per file and 101 MB per multipart request by the application. Individual API operations can impose lower limits. Temporary multipart files require writable temporary storage. Avoid logging request bodies or OAuth traffic.
