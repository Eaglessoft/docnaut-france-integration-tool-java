# Contributing

Build with Java 25 and Maven 3.9 from this repository root:

```sh
mvn verify
```

Keep changes focused on this integration client. Use Spring's supported HTTP, security and template facilities. Keep API calls on the server, retain CSRF protection, and render remote response text as text rather than HTML.

For a behavior change, extend the existing HTTP feature journey with the relevant successful and unsuccessful cases. Use controlled local OAuth and API endpoints with synthetic data; do not require a shared account or send test documents to a live provider. See [development guidance](docs/development.md).

Describe the user-visible change and validation in your pull request. Update the operation catalogue and bundled contract together when deliberately adopting an API change. Do not commit build output, logs, tokens, client secrets or real documents.

Report vulnerabilities through the private process described in [SECURITY.md](SECURITY.md). Contributions are provided under the project's [MIT license](LICENSE).
