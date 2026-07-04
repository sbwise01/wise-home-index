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
- CI/CD is implemented; operational docs are in the README ("CI/CD"). The design
  rationale, invariants, and remaining follow-ups are under "CI/CD design decisions
  & state" below — read it before changing `.github/workflows/`.

## CI/CD design decisions & state

Pipeline: `.github/workflows/pr.yml`, `.github/workflows/release.yml`, and the
`.github/scripts/pom_version.py` helper. Operator-facing usage is in the README;
this section is the "why" and what's left.

**Version management — home-grown Conventional Commits (deliberately not `release-please`).**

- PR title is the Conventional Commit; `pom.xml` `<version>` is the single source
  of truth; a git tag equal to the pom version (raw value, no `v` prefix) marks
  each release. Invariant on `main`: the pom version always has a matching tag.
- Both workflows are `paths`-scoped to `src/**`, `Dockerfile`, `.dockerignore`,
  `pom.xml`, so metadata-only changes never test/build/release. This composes with the version
  flow: a code PR touches `src`/`Dockerfile`, `pr.yml` then bumps `pom.xml`, and
  the merge therefore always includes a `pom.xml` change so `release.yml` fires.
- Bump derives from the **PR title**, not commit history — squash-merge friendly
  and it's the thing we already validate. Easy to switch to commit-scanning later.
- Any valid non-`feat`/non-breaking title → **patch**, so every merge advances the
  version (release tags on every merge; a "no-bump" PR would collide on the tag).
- `pr.yml` bumps from **main's** pom version (not the PR's own), which is what makes
  the bump idempotent across re-runs after the bot has already committed a bump.
- **No bot re-run loop:** the bump commit message includes `[skip ci]`, so GitHub does
  not trigger `pr.yml` on that push at all (unlike the `github.actor` guard alone,
  which still queues the workflow and can prompt for approval before skipping the job).
  Squash-merge uses the PR title on `main`, so `[skip ci]` on the bump commit does
  not suppress `release.yml`. The `github.actor` guard remains as a backup.
- **Burned versions:** `release.yml` creates the tag *before* the image push, so a
  failed publish leaves a reserved tag with no image; the next PR simply bumps past
  it. A "tag already exists" guard means versions are never reused.
- **Concurrent-PR version race** is prevented by the PR up-to-date check plus the
  branch-protection rule "Require branches to be up to date before merging".
- **Caveat — required check on head SHA:** after the bot bump, the PR head is the
  bot commit and `pr.yml` deliberately does not re-run there, so the required check
  status won't appear on that final SHA. Merge as admin (Option A), or (future) push
  the bump with a PAT/GitHub App so checks re-run and converge (Option B; the bump
  step is already idempotent).

**Secrets — GitHub OIDC → AWS SSM, Docker Hub kept.**

- `release.yml` assumes IAM role `dockerhub-role`
  (`arn:aws:iam::712671171381:role/dockerhub-role`, region `us-east-2`, provisioned
  in `wise-aws-terraform-bootstrap`) via GitHub OIDC, then reads
  `/dockerhub/api/username` + `/dockerhub/api/token` (SecureString, SOPS-managed)
  from SSM at runtime — same pattern as wise-k8s `terraform-plan.yml`'s Infracost
  key. No long-lived Docker Hub secret lives in GitHub; rotation is central in SSM.
- Rejected alternatives: GitHub environment secrets (still a long-lived secret in
  GitHub); GHCR / ECR (would change the cluster's image pull path). Docker Hub has
  no native keyless/OIDC login, hence the SSM approach.

**Cross-repo deploy — out of scope.** Rolling the new tag into `wise-k8s`
`deployment.yaml` is delegated to **FluxCD image update automation** (watches the
Docker Hub repo); `release.yml` stops at pushing the image.

**Version state:** reconciled at `2.0.0` across `pom.xml`, git tag `2.0.0` (commit
`bd234e0`, present locally and on the remote), the Docker Hub image, and
`wise-k8s .../deployment.yaml`.

**Open follow-up:** `wise-k8s` overlay cleanup — drop the now-unused
`applications.yaml` ConfigMap and `WISE_HOME_INDEX_CONFIG` mount; optionally set
`WISE_HOME_INDEX_REFRESH_SECONDS`. (The deployment image tag is already `2.0.0`.)
