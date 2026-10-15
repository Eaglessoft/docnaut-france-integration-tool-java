# Docnaut France Integration Tool — Java

A standalone Java web application for exploring the Docnaut France Client API. Choose Sandbox or Production, sign in with your service client's credentials, and use server-rendered forms to submit requests and inspect responses.

The application uses Java 25, Spring Boot 4.1 and Thymeleaf. API and OAuth requests run on the server. You need an existing service client with permissions for the operations you want to use.

## Run from source

Install JDK 25 and Maven 3.9, then run from this repository root:

```sh
mvn verify
mvn spring-boot:run
```

Open [localhost:8094](http://localhost:8094). Select an environment and enter your client ID and secret. Production actions can change real data.

For a container, see [container and deployment instructions](docs/deployment.md). The included [Dev Container](.devcontainer/devcontainer.json) provides Java and Maven.

## What you can do

| Screen | Operations |
|---|---|
| Overview and settings | Read your profile and configuration, replace configuration with an ETag, rotate your service-client secret |
| Peppol facts | Read API-reported Peppol facts |
| Documents | Upload documents, browse documents, inspect processing, events and artifacts, download artifacts, record inbound decisions |
| Payments and reporting | Upload original payment CDAR (212) through Send a document, read lifecycle history, submit a reporting transmission, read reports and receipts |
| Webhooks and deliveries | Create, inspect, update and verify subscriptions, rotate webhook secrets, inspect delivery history |

Available actions depend on the API's permissions and document state. Submission acceptance does not establish final delivery. The tool displays API results; it does not implement invoice routing, government submission or a webhook receiver.

## Documentation

- [Configuration and credentials](docs/configuration.md)
- [Using the tool](docs/usage.md)
- [Architecture and development](docs/development.md)
- [Docker Compose and Kubernetes](docs/deployment.md)
- [Contributing](CONTRIBUTING.md), [security](SECURITY.md) and [changes](CHANGELOG.md)

The bundled [Client API contract](contracts/client-api-v1.yaml) is a versioned reference snapshot. The implemented operation catalogue is in [ClientApiOperation.java](src/main/java/com/docnaut/clienttestportal/api/ClientApiOperation.java); the tool does not claim to expose every contract operation.

## Project status and license

Source: [Eaglessoft/docnaut-france-integration-tool-java](https://github.com/Eaglessoft/docnaut-france-integration-tool-java).

This repository builds one runnable application. Its internal HTTP client is not an independently published Java SDK. No Maven Central publication or live provider certification is implied.

Project code is licensed under [MIT](LICENSE). See [third-party notices](THIRD-PARTY-NOTICES.md) for dependency and branding information.
