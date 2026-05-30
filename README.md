# wise-home-index

Index page for services hosted on the **wise-k8s** homelab cluster.

A small Java REST web service (JAX-RS / Jersey on Apache Tomcat) that renders an
index page of homelab applications. The set of applications shown depends on
where the request comes from:

| Request origin                | Shows                          |
| ----------------------------- | ------------------------------ |
| Private LAN                   | public **and** private URLs    |
| Anywhere else (internet)      | public URLs only               |

* **Public URLs** are sub-domains of `home.bradandmarsha.com`.
* **Private URLs** are short names.

Applications are described in a YAML file. Each entry needs a `name` and a
`url`; an `image` is optional and falls back to a default tile.

## Configuration

```yaml
applications:
  - name: Grafana
    url: https://grafana.home.bradandmarsha.com   # public (subdomain)
    image: https://grafana.home.bradandmarsha.com/public/img/grafana_icon.svg
  - name: My Dashboard
    url: https://my-dashboard                     # private (short name)
```

The configuration file is resolved in this order:

1. System property `wise.home.index.config`
2. Environment variable `WISE_HOME_INDEX_CONFIG`
3. The bundled sample `applications.yaml` (used if neither of the above is set
   or the configured path is not readable)

### Trusted private subnet

The subnet whose requests may see private URLs defaults to `192.168.0.0/24`.
Override it (IPv4 CIDR notation) with either:

1. System property `wise.home.index.private.cidr`
2. Environment variable `WISE_HOME_INDEX_PRIVATE_CIDR`

An invalid value logs a warning and falls back to the default.

## Endpoints

| Method | Path                | Description                                  |
| ------ | ------------------- | -------------------------------------------- |
| `GET`  | `/`                 | HTML index page (filtered by request origin) |
| `GET`  | `/api/applications` | JSON list of visible applications            |
| `GET`  | `/health`           | JSON health check (`{"status":"UP"}`)        |

## Build

Requires JDK 17 and Maven (or just use Docker, below).

```bash
mvn clean package
# produces target/wise-home-index.war
```

## Run with Docker

```bash
# Build the image (multi-stage: Maven build + Tomcat runtime)
docker build --platform linux/amd64 -t sbwise/wise-home-index .

# Run with the bundled sample config
docker run --rm -p 8080:8080 sbwise/wise-home-index

# Run with your own config mounted at the default location
docker run --rm -p 8080:8080 \
  -e WISE_HOME_INDEX_PRIVATE_CIDR="192.168.40.0/24" \
  -v "$(pwd)/my-applications.yaml:/config/applications.yaml:ro" \
  sbwise/wise-home-index
```

Then open <http://localhost:8080/>.

> Note: because subnet classification is based on the client IP, requests from
> the Docker host appear as their bridge/loopback address
> so you will see the public view locally. Behind the
> cluster ingress, the `X-Forwarded-For` / `X-Real-IP` header is honored to
> recover the real client IP.

## Deploy on Kubernetes

Mount the application map from a `ConfigMap` at `/config/applications.yaml` (the
image's default `WISE_HOME_INDEX_CONFIG` path) and expose port `8080`. Preserve
the client source IP (e.g. `externalTrafficPolicy: Local` or an ingress that
sets `X-Forwarded-For`) so the private-network detection works.
