# wise-home-index

A Java REST web service that renders an index page for the services hosted on
the **wise-k8s** homelab cluster. The set of services shown is filtered by the
request's network origin.

## What it does

- Serves an HTML index page of homelab applications as clickable tiles.
- Filters visible applications by request origin:
 - Requests from the private LAN see **public and private** URLs.
 - Requests whose client IP matches a current public IP of the home host
   (`home.bradandmarsha.com`, e.g. LAN clients arriving via hairpin NAT) also see
   **public and private** URLs.
 - All other (internet) requests see **public URLs only**.
- URL classification (derived automatically from each URL):
  - **Public**  = sub-domains of `home.bradandmarsha.com`.
  - **Private** = short names.
- Reads the application list from a YAML file; each entry has a required `name`
  and `url`, plus an optional `image`. Entries without an image fall back to a
  bundled default tile.

## Tech stack

- **Java 17**, packaged as a WAR with **Maven** (`war` packaging).
- **JAX-RS via Jersey 3.1** (Jakarta namespace) for the REST layer.
- **Apache Tomcat 10.1** as the servlet container / web server.
- **SnakeYAML** for configuration parsing; **Jackson** for JSON responses.
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
  config/ConfigLoader.java       Resolves + parses + validates the YAML config
  model/ApplicationEntry.java    One application; isPublic() classifies the URL
  model/IndexConfig.java         Root YAML object ({ applications: [...] })
  service/IndexService.java      Holds config; filters apps by visibility scope
  util/NetworkUtil.java          Client-IP resolution + 192.168.0.0/24 check
  rest/JaxRsApplication.java     Jersey ResourceConfig; binds IndexService
  rest/IndexResource.java        GET /  -> HTML
  rest/HtmlRenderer.java         Builds the index page HTML (no template engine)
  rest/ApplicationResource.java  GET /api/applications -> JSON
  rest/HealthResource.java       GET /health -> JSON
src/main/resources/applications.yaml   Bundled sample config (fallback)
src/main/webapp/WEB-INF/web.xml        Jersey servlet filter (forwardOn404)
src/main/webapp/images/default-tile.svg Default tile image
src/test/java/.../NetworkUtilTest.java  Subnet classification tests
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
- **Public vs private** is decided per URL in `ApplicationEntry.isPublic()`:
  the host must equal or be a sub-domain of `home.bradandmarsha.com`.
- **Static assets** (the default tile) are served by Tomcat's default servlet.
  Jersey is registered as a *filter* with
  `jersey.config.servlet.filter.forwardOn404=true` so unmatched paths fall
  through to static serving (see `web.xml`).

## Configuration

The YAML config path is resolved in this order:

1. System property `wise.home.index.config`
2. Environment variable `WISE_HOME_INDEX_CONFIG`
3. Bundled `applications.yaml` (used if neither is set or the path is unreadable)

The trusted private subnet is resolved similarly (system property
`wise.home.index.private.cidr`, then env var `WISE_HOME_INDEX_PRIVATE_CIDR`,
then the `192.168.0.0/24` default).

The trusted home host is resolved the same way (system property
`wise.home.index.public.host`, then env var `WISE_HOME_INDEX_PUBLIC_HOST`, then
the `home.bradandmarsha.com` default).

```yaml
applications:
  - name: Grafana
    url: https://grafana.home.bradandmarsha.com   # public (subdomain)
    image: https://grafana.home.bradandmarsha.com/public/img/grafana_icon.svg
  - name: Ceph Dashboard
    url: https://ceph-dashboard                   # private (short name)
```

## Build & run

```bash
# Local build (requires JDK 17 + Maven) -> target/wise-home-index.war
mvn clean package

# Docker (no local JDK/Maven needed)
docker build --platform linux/amd64 -t sbwise/wise-home-index .
docker run --rm -p 8080:8080 sbwise/wise-home-index                # bundled sample config
docker run --rm -p 8080:8080 \
  -e WISE_HOME_INDEX_PRIVATE_CIDR="192.168.40.0/24" \
  -e WISE_HOME_INDEX_PUBLIC_HOST="home.bradandmarsha.com" \
  -v "$(pwd)/my-apps.yaml:/config/applications.yaml:ro" \
  sbwise/wise-home-index                                           # custom config

# then open http://localhost:8080/
```

The image's default `WISE_HOME_INDEX_CONFIG=/config/applications.yaml` is the
conventional mount point for a Kubernetes `ConfigMap`.

## Notes for future changes

- Keep the Jakarta (not `javax`) namespace — Tomcat 10.1 / Jersey 3.x require it.
- The HTML is built by hand in `HtmlRenderer` (no template engine) and all
  user-supplied values are HTML-escaped; preserve that when editing.
- Private-network detection depends on seeing the real client IP; deployments
  must preserve it (ingress `X-Forwarded-For`, or `externalTrafficPolicy: Local`).
