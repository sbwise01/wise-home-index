# wise-home-index

Index page for services hosted on the **wise-k8s** homelab cluster.

A small Java REST web service (JAX-RS / Jersey on Apache Tomcat) that renders an
index page of homelab applications. The set of applications shown depends on
where the request comes from:

| Request origin                          | Shows                          |
| --------------------------------------- | ------------------------------ |
| Private LAN                             | public **and** private apps    |
| The home host's public IP (hairpin NAT) | public **and** private apps    |
| Anywhere else (internet)                | public apps only               |

* **Public apps** are served through the `nginx` ingress class.
* **Private apps** are served through the `nginx-internal` ingress class.

## Application discovery

Applications are discovered automatically from Kubernetes `Ingress` resources
across all namespaces — there is no static config file. An ingress opts in by
setting `index.home.bradandmarsha.com/enabled: "true"` and describes its tile via
annotations:

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  annotations:
    index.home.bradandmarsha.com/enabled: "true"                    # required
    index.home.bradandmarsha.com/name: "Grafana Dashboard"          # required
    index.home.bradandmarsha.com/image: "https://.../grafana.jpg"   # optional
    index.home.bradandmarsha.com/description: "Metrics dashboards"   # optional
    index.home.bradandmarsha.com/weight: "40"                       # optional, sort order
spec:
  ingressClassName: nginx           # public; use nginx-internal for private
  rules:
    - host: grafana-dashboard.home.bradandmarsha.com
```

* The tile **URL** is derived from the ingress host (`https://<host>` when TLS is
  configured, otherwise `http://<host>`).
* **Visibility** comes from the ingress class: `nginx` → public,
  `nginx-internal` → private. Any other/absent class is treated as private.
* Tiles are ordered by `weight` ascending (lower first), then by name.
* An enabled ingress missing `name` or a usable host is skipped (with a warning).

The Kubernetes API client is created with `ClientBuilder.standard()`: it uses the
in-cluster service account when running in Kubernetes, and falls back to the local
kubeconfig for development. Results are cached and refreshed on an interval
(default 5 minutes); if the cluster is unreachable the last-known list is kept and
the page still serves.

### Discovery settings

Each is resolved as: system property, then environment variable, then default.

| Setting               | System property                         | Env variable                            | Default                        |
| --------------------- | --------------------------------------- | --------------------------------------- | ------------------------------ |
| Annotation prefix     | `wise.home.index.annotation.prefix`     | `WISE_HOME_INDEX_ANNOTATION_PREFIX`     | `index.home.bradandmarsha.com` |
| Public ingress class  | `wise.home.index.ingress.class.public`  | `WISE_HOME_INDEX_INGRESS_CLASS_PUBLIC`  | `nginx`                        |
| Private ingress class | `wise.home.index.ingress.class.private` | `WISE_HOME_INDEX_INGRESS_CLASS_PRIVATE` | `nginx-internal`               |
| Refresh interval (s)  | `wise.home.index.refresh.seconds`       | `WISE_HOME_INDEX_REFRESH_SECONDS`       | `300`                          |

### Trusted request origins

A request may see private URLs when its client IP either falls within the
trusted private subnet **or** matches a current public IP of the trusted home
host. The latter covers LAN clients that reach the page via the public domain
(hairpin NAT), where the observed source IP is the home's WAN address rather
than a LAN address.

The subnet defaults to `192.168.0.0/24`. Override it (IPv4 CIDR notation) with
either:

1. System property `wise.home.index.private.cidr`
2. Environment variable `WISE_HOME_INDEX_PRIVATE_CIDR`

An invalid value logs a warning and falls back to the default.

The home host defaults to `home.bradandmarsha.com`. Override it with either:

1. System property `wise.home.index.public.host`
2. Environment variable `WISE_HOME_INDEX_PUBLIC_HOST`

Its IP addresses are re-resolved via DNS on demand and cached for 5 minutes (to
tolerate dynamic DNS). Only IPv4 addresses are considered; transient DNS
resolution failures keep the last-known IPs.

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

# Local dev against a cluster: mount your kubeconfig so discovery can reach it
docker run --rm -p 8080:8080 \
  -e WISE_HOME_INDEX_PRIVATE_CIDR="192.168.40.0/24" \
  -e WISE_HOME_INDEX_PUBLIC_HOST="home.bradandmarsha.com" \
  -v "$HOME/.kube:/root/.kube:ro" -e KUBECONFIG=/root/.kube/config \
  sbwise/wise-home-index
```

Without a reachable cluster the app still starts and serves an empty index
(logging a warning) until discovery succeeds.

Then open <http://localhost:8080/>.

> Note: because subnet classification is based on the client IP, requests from
> the Docker host appear as their bridge/loopback address
> so you will see the public view locally. Behind the
> cluster ingress, the `X-Forwarded-For` / `X-Real-IP` header is honored to
> recover the real client IP.

## Deploy on Kubernetes

Run the deployment with a service account bound to a ClusterRole granting
`get/list/watch` on `networking.k8s.io/ingresses` (see the wise-k8s deployment's
`rbac.yaml`) so discovery can read Ingress resources cluster-wide, and expose
port `8080`. Preserve the client source IP (e.g. `externalTrafficPolicy: Local`
or an ingress that sets `X-Forwarded-For`) so the private-network detection works.
