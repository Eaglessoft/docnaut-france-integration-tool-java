# Architecture and development

The application is a single Spring Boot Maven project. Controllers handle forms and render Thymeleaf templates. `ClientApiClient` sends requests selected from `ClientApiOperation`; `TokenClient` obtains OAuth client-credentials tokens. Spring Security manages login, sessions, CSRF and logout. These classes are part of the application, not a separately versioned SDK.

No database, message broker or browser build pipeline is required. The target API and identity provider are external services.

## Local development

Use JDK 25 and Maven 3.9, or open the repository in its included Dev Container.

```sh
mvn verify
mvn spring-boot:run
```

Open `http://localhost:8094`. Stop the process with Ctrl+C. To run the packaged application:

```sh
java -jar target/docnaut-france-integration-tool-java-0.1.0.jar
```

Configuration is documented in [configuration.md](configuration.md).

## Local browser fixture

For a controlled visual check without an API account:

```sh
mvn -Dtest=PortalFeatureE2ETest -Dportal.browser=true test
```

Open `http://localhost:8083/client-test/login` and use `operator` / `fixture` with either environment.
Both choices target synthetic local partners. Forward port 8083 when using a Dev Container.
This opt-in mode holds the application open and does not execute test assertions; stop it with Ctrl+C.
Run ordinary `mvn verify` separately for automated acceptance.

## Validation

`mvn verify` compiles, runs tests and packages the application. The HTTP feature journey starts the real application listener and controlled local OAuth/API partners with synthetic data. It exercises browser-facing HTTP and server-side requests; it does not validate browser rendering or a live remote service.

For interface changes, also check a desktop browser journey: login, the changed workflow, response/error presentation and logout. Do not use real credentials or business documents in test fixtures.

The [bundled contract](../contracts/client-api-v1.yaml) supports offline review of the intended Client API. Changes to the remote service can require an explicit client update.
