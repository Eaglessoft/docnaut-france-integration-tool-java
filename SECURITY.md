# Security

This application handles service-client credentials, documents and API responses. Credentials and tokens remain in the server-side session so the application can renew tokens. Signing out invalidates that session; process restarts discard in-memory sessions.

Outbound endpoints require HTTPS. Explicit insecure HTTP is restricted to loopback development endpoints. The operation catalogue limits outbound API paths, and Spring Security protects forms with CSRF tokens. These controls do not replace secure hosting.

Keep local access restricted. Before shared hosting, configure HTTPS and secure session cookies as described in [configuration](docs/configuration.md). Do not include secrets or real documents in screenshots, logs, issues or examples. Secret-rotation responses can contain new credentials.

## Reporting vulnerabilities

Use the repository's private security-reporting feature if enabled. Otherwise contact a maintainer through an existing verified private channel. If no such channel is available, request one without publishing exploit details or sensitive data.

Include the affected revision, impact and a minimal reproduction using synthetic data. This project does not declare a dedicated security mailbox, response-time commitment or long-term support window.
