# Containers and deployment

Run these commands from the standalone repository root. The application uses port 8080 inside its image and runs as a non-root user. It needs outbound access to the configured OAuth and API endpoints.

## Build locally

```sh
docker build -t docnaut-java-client:local .
docker run --rm -p 127.0.0.1:8094:8080 docnaut-java-client:local
```

Open `http://localhost:8094`. The default image build packages the application. Run `docker build --target verification .` to execute Maven verification; CI runs that target before publication. The repository workflow builds and publishes eligible revisions to `ghcr.io/eaglessoft/docnaut-france-integration-tool-java`; check available tags before pulling.

## Docker Compose

```sh
docker compose up -d
docker compose logs -f
```

The [Compose file](../compose.yaml) binds `127.0.0.1:8094` and supplies the default endpoint addresses. Set `DOCNAUT_CLIENT_IMAGE` to select an available tag or digest, or use the locally built image:

```sh
DOCNAUT_CLIENT_IMAGE=docnaut-java-client:local docker compose up -d
```

Override endpoint variables through your shell before starting Compose. Client credentials are entered through the UI. Stop this deployment with `docker compose down`.

## Kubernetes

Inspect the [manifests](../infra/kube/kustomization.yaml) and select an image tag or digest for your deployment before applying them to your intended cluster and namespace.

```sh
kubectl apply -k infra/kube
kubectl port-forward service/docnaut-france-integration-tool-java 8094:80
```

Open `http://localhost:8094`. The manifests create one Deployment and one ClusterIP Service. They do not create ingress, certificates, databases or credentials.

The Deployment uses one replica and Recreate updates because sessions live in process memory. Restarting or replacing the pod signs users out. Login probes show application availability; they do not check remote OAuth or API connectivity.

For shared access, configure HTTPS and secure cookies using [configuration guidance](configuration.md). Size memory for expected uploads and concurrency; the supplied resource values are starting settings, not capacity guarantees.
