# wise-home-index

A Java REST web service that renders an index page for the services hosted on
the **wise-k8s** homelab cluster. The set of services shown is filtered by the
request's network origin.

## What it does

- Serves an HTML index page of homelab applications as clickable tiles.
- **Discovers applications automatically from Kubernetes `Ingress` resources**
  (cluster-wide) — there is no static application list. An ingress opts in with
  the annotation `index.home.bradandmarsha.com/enabled: "true"`.
- Filters visible applications by request origin:
 - Requests from the private LAN see **public and private** apps.
 - Requests whose client IP matches a current public IP of the home host
   (`home.bradandmarsha.com`, e.g. LAN clients arriving via hairpin NAT) also see
   **public and private** apps.
 - All other (internet) requests see **public apps only**.
- Public vs private classification (derived from the ingress class):
  - **Public**  = ingress class `nginx`.
  - **Private** = ingress class `nginx-internal`.
  - Any other/absent class is treated as **private** (never exposed publicly).
- Each app is built from the ingress `index.home.bradandmarsha.com/*` annotations
  (`name` required; `image`, `description`, `weight` optional) and its host
  (URL = `https://<host>` when TLS is present, else `http://<host>`). Tiles are
  ordered by `weight` ascending (lower first), then name. Apps without an image
  fall back to a bundled default tile.

## Tech stack

- **Java 17**, packaged as a WAR with **Maven** (`war` packaging).
- **JAX-RS via Jersey 3.1** (Jakarta namespace) for the REST layer.
- **Apache Tomcat 10.1** as the servlet container / web server.
- **Kubernetes Java client** (`io.kubernetes:client-java`) for Ingress discovery;
  **Jackson** for JSON responses.
- Containerized with a multi-stage **Dockerfile** (Maven build → Tomcat runtime),
  deployed as `ROOT.war` so the index page is served from `/`.

## Endpoints

| Method | Path                | Description                                  |
| ------ | ------------------- | -------------------------------------------- |
| `GET`  | `/`                 | HTML index page (filtered by request origin) |
| `GET`  | `/api/applications` | JSON list of visible applications + scope    |
| `GET`  | `/health`           | JSON health check (`{"status":"UP"}`)        |

## Project layout

```
pom.xml                          Maven build (war packaging, JDK 17)
Dockerfile                       Multi-stage build + Tomcat runtime
src/main/java/com/bradandmarsha/wisehomeindex/
  model/ApplicationEntry.java         One application (name/url/image/description/weight/public)
  discovery/ApplicationSource.java    Interface: supplies ordered applications
  discovery/DiscoverySettings.java    Annotation prefix, ingress classes, refresh interval
  discovery/IngressMapper.java        Pure Ingress -> ApplicationEntry mapping (client-agnostic)
  discovery/IngressApplicationSource.java  Lists Ingresses via k8s API; caches with TTL
  service/IndexService.java           Filters discovered apps by visibility scope
  util/NetworkUtil.java               Client-IP resolution + 192.168.0.0/24 check
  rest/JaxRsApplication.java          Jersey ResourceConfig; binds IndexService
  rest/IndexResource.java             GET /  -> HTML
  rest/HtmlRenderer.java              Builds the index page HTML (no template engine)
  rest/ApplicationResource.java       GET /api/applications -> JSON
  rest/HealthResource.java            GET /health -> JSON
src/main/webapp/WEB-INF/web.xml        Jersey servlet filter (forwardOn404)
src/main/webapp/images/default-tile.svg Default tile image
src/test/java/.../NetworkUtilTest.java  Subnet classification tests
src/test/java/.../IngressMapperTest.java Ingress annotation/class mapping tests
src/test/java/.../IndexServiceTest.java Visibility-filtering tests
```

## How key requirements are implemented

- **Request-context filtering** lives in `NetworkUtil`. It resolves the client
 IP from `X-Forwarded-For`, then `X-Real-IP`, then `remoteAddr` (so it works
 behind a k8s ingress). A request is trusted (sees private URLs) when its client
 IP either falls within the trusted subnet **or** matches a current public IP of
 the trusted home host:
 - The subnet defaults to `192.168.0.0/24` but is configurable at startup via the
   `wise.home.index.private.cidr` system property or `WISE_HOME_INDEX_PRIVATE_CIDR`
   environment variable (IPv4 CIDR notation; invalid values fall back to the default).
 - The home host defaults to `home.bradandmarsha.com` but is configurable via the
   `wise.home.index.public.host` system property or `WISE_HOME_INDEX_PUBLIC_HOST`
   environment variable. Its IPs are re-resolved via DNS on demand and cached for
   5 minutes (handles dynamic DNS); only IPv4 addresses are considered, and a
   transient DNS failure keeps the last-known IPs. The matching core is the
   package-private `NetworkUtil.matchesPublicHostIp(ip, trustedSet)`, which takes
   an injected IP set so it can be unit-tested without real DNS.
- **Application discovery** lives in the `discovery` package. `IngressApplicationSource`
  lists `Ingress` resources across all namespaces via the Kubernetes API and maps
  the opted-in ones to `ApplicationEntry`. The Kubernetes client is created with
  `ClientBuilder.standard()`, which uses the in-cluster service account when running
  in Kubernetes and falls back to the local kubeconfig for development. Results are
  cached for a configurable interval (default 5 minutes) and refreshed lazily on the
  first request after expiry; if a refresh fails (transient API error, or no cluster) the
  last-known list is retained and the page keeps serving. The pure mapping logic is
  in `IngressMapper` (client-agnostic, unit-tested without a live cluster).
- **Public vs private** is decided by the ingress class in `IngressMapper`
  (`nginx` -> public, `nginx-internal` -> private; unknown -> private) and stored on
  `ApplicationEntry.isPublic()`. The class is read from `spec.ingressClassName`,
  falling back to the deprecated `kubernetes.io/ingress.class` annotation.
- **Static assets** (the default tile) are served by Tomcat's default servlet.
  Jersey is registered as a *filter* with
  `jersey.config.servlet.filter.forwardOn404=true` so unmatched paths fall
  through to static serving (see `web.xml`).

## Configuration

Applications are discovered from the cluster; there is no config file. Discovery
behavior is tunable (each: system property, then environment variable, then default):

| Setting              | System property                          | Env variable                          | Default                        |
| -------------------- | ---------------------------------------- | ------------------------------------- | ------------------------------ |
| Annotation prefix    | `wise.home.index.annotation.prefix`      | `WISE_HOME_INDEX_ANNOTATION_PREFIX`   | `index.home.bradandmarsha.com` |
| Public ingress class | `wise.home.index.ingress.class.public`   | `WISE_HOME_INDEX_INGRESS_CLASS_PUBLIC`| `nginx`                        |
| Private ingress class| `wise.home.index.ingress.class.private`  | `WISE_HOME_INDEX_INGRESS_CLASS_PRIVATE`| `nginx-internal`              |
| Refresh interval (s) | `wise.home.index.refresh.seconds`        | `WISE_HOME_INDEX_REFRESH_SECONDS`     | `300`                          |

The trusted private subnet (system property `wise.home.index.private.cidr`, then
env var `WISE_HOME_INDEX_PRIVATE_CIDR`, then `192.168.0.0/24`) and the trusted home
host (`wise.home.index.public.host` / `WISE_HOME_INDEX_PUBLIC_HOST` /
`home.bradandmarsha.com`) are resolved the same way.

Recognized ingress annotations (with the default prefix):

```yaml
metadata:
  annotations:
    index.home.bradandmarsha.com/enabled: "true"          # required to be shown
    index.home.bradandmarsha.com/name: "Grafana Dashboard" # required
    index.home.bradandmarsha.com/image: "https://.../grafana.jpg"  # optional
    index.home.bradandmarsha.com/description: "Metrics dashboards"  # optional
    index.home.bradandmarsha.com/weight: "40"              # optional (sort order)
spec:
  ingressClassName: nginx           # public   (nginx-internal => private)
```

The Kubernetes API access requires RBAC granting `get/list/watch` on
`networking.k8s.io/ingresses` to the `wise-home-index` service account (defined in
the wise-k8s deployment's `rbac.yaml`).

## Build & run

```bash
# Local build (requires JDK 17 + Maven) -> target/wise-home-index.war
mvn clean package

# Docker (no local JDK/Maven needed)
docker build --platform linux/amd64 -t sbwise/wise-home-index .

# In-cluster: relies on the mounted service-account token (no extra config).
# Local dev against a cluster: mount your kubeconfig so ClientBuilder can find it.
docker run --rm -p 8080:8080 \
  -e WISE_HOME_INDEX_PRIVATE_CIDR="192.168.40.0/24" \
  -e WISE_HOME_INDEX_PUBLIC_HOST="home.bradandmarsha.com" \
  -v "$HOME/.kube:/root/.kube:ro" -e KUBECONFIG=/root/.kube/config \
  sbwise/wise-home-index

# then open http://localhost:8080/
```

Without a reachable cluster the app still starts and serves an empty index
(logging a warning) until discovery succeeds.

## Notes for future changes

- Keep the Jakarta (not `javax`) namespace — Tomcat 10.1 / Jersey 3.x require it.
- The HTML is built by hand in `HtmlRenderer` (no template engine) and all
  user-supplied values are HTML-escaped; preserve that when editing.
- Private-network detection depends on seeing the real client IP; deployments
  must preserve it (ingress `X-Forwarded-For`, or `externalTrafficPolicy: Local`).
- Discovery needs the ingress RBAC ClusterRole/Binding; keep them in sync with the
  service account when changing namespaces or resource scope.
