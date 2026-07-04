package com.bradandmarsha.wisehomeindex.discovery;

import com.bradandmarsha.wisehomeindex.model.ApplicationEntry;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.NetworkingV1Api;
import io.kubernetes.client.openapi.models.V1Ingress;
import io.kubernetes.client.openapi.models.V1IngressSpec;
import io.kubernetes.client.openapi.models.V1IngressTLS;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.util.ClientBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * {@link ApplicationSource} backed by the Kubernetes API. Lists {@code Ingress}
 * resources across all namespaces and maps the opted-in ones (see
 * {@link IngressMapper}) into {@link ApplicationEntry} instances.
 *
 * <p>Results are cached for {@link DiscoverySettings#getRefreshInterval()} and
 * refreshed lazily on the first request after the cache expires. If a refresh
 * fails (e.g. transient API error), the last-known list is retained rather than
 * being cleared.</p>
 *
 * <p>The API client is created via {@link ClientBuilder#standard()}, which uses
 * the in-cluster service account when running inside Kubernetes and falls back
 * to the local kubeconfig for development.</p>
 */
public class IngressApplicationSource implements ApplicationSource {

    private static final Logger LOG = Logger.getLogger(IngressApplicationSource.class.getName());

    /** Deprecated ingress-class annotation, used as a fallback when spec.ingressClassName is absent. */
    private static final String LEGACY_INGRESS_CLASS_ANNOTATION = "kubernetes.io/ingress.class";

    private static final Comparator<ApplicationEntry> DISPLAY_ORDER =
            Comparator.comparingInt(ApplicationEntry::getWeight)
                    .thenComparing(e -> e.getName() == null ? "" : e.getName(), String.CASE_INSENSITIVE_ORDER);

    private final DiscoverySettings settings;
    private final IngressMapper mapper;
    private final long refreshMillis;

    private volatile NetworkingV1Api api;
    private volatile List<ApplicationEntry> cache = List.of();
    private volatile long expiresAt = 0L;
    private volatile boolean loadedAtLeastOnce = false;

    public IngressApplicationSource(DiscoverySettings settings) {
        this.settings = settings;
        this.mapper = new IngressMapper(settings);
        this.refreshMillis = settings.getRefreshInterval().toMillis();
    }

    /** Test seam: inject a pre-built API (e.g. a mock) instead of building one from cluster config. */
    IngressApplicationSource(DiscoverySettings settings, NetworkingV1Api api) {
        this(settings);
        this.api = api;
    }

    @Override
    public List<ApplicationEntry> getApplications() {
        if (loadedAtLeastOnce && System.currentTimeMillis() < expiresAt) {
            return cache;
        }
        return refresh();
    }

    private synchronized List<ApplicationEntry> refresh() {
        // Another thread may have refreshed while we waited on the lock.
        if (loadedAtLeastOnce && System.currentTimeMillis() < expiresAt) {
            return cache;
        }

        try {
            NetworkingV1Api client = api();
            List<V1Ingress> ingresses = client.listIngressForAllNamespaces().execute().getItems();
            List<ApplicationEntry> discovered = ingresses.stream()
                    .map(this::toView)
                    .map(mapper::map)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .sorted(DISPLAY_ORDER)
                    .collect(Collectors.toList());
            cache = List.copyOf(discovered);
            loadedAtLeastOnce = true;
            LOG.info(() -> "Discovered " + cache.size() + " application(s) from Ingress resources");
        } catch (ApiException ex) {
            LOG.log(Level.WARNING, ex, () -> "Failed to list Ingress resources (HTTP " + ex.getCode()
                    + "): " + ex.getResponseBody() + "; keeping last-known "
                    + (loadedAtLeastOnce ? cache.size() + " application(s)" : "empty list"));
        } catch (IOException ex) {
            LOG.log(Level.WARNING, ex, () -> "Unable to initialize Kubernetes client; keeping last-known "
                    + (loadedAtLeastOnce ? cache.size() + " application(s)" : "empty list"));
        } catch (RuntimeException ex) {
            LOG.log(Level.WARNING, ex, () -> "Unexpected error during Ingress discovery; keeping last-known "
                    + (loadedAtLeastOnce ? cache.size() + " application(s)" : "empty list"));
        }

        expiresAt = System.currentTimeMillis() + refreshMillis;
        return cache;
    }

    private NetworkingV1Api api() throws IOException {
        NetworkingV1Api existing = api;
        if (existing != null) {
            return existing;
        }
        ApiClient client = ClientBuilder.standard().build();
        NetworkingV1Api built = new NetworkingV1Api(client);
        api = built;
        return built;
    }

    private IngressMapper.IngressView toView(V1Ingress ingress) {
        V1ObjectMeta meta = ingress.getMetadata();
        String name = meta != null ? meta.getName() : null;
        String namespace = meta != null ? meta.getNamespace() : null;
        Map<String, String> annotations = meta != null ? meta.getAnnotations() : null;

        V1IngressSpec spec = ingress.getSpec();
        String ingressClass = spec != null ? spec.getIngressClassName() : null;
        if (ingressClass == null && annotations != null) {
            ingressClass = annotations.get(LEGACY_INGRESS_CLASS_ANNOTATION);
        }

        List<String> ruleHosts = new ArrayList<>();
        List<String> tlsHosts = new ArrayList<>();
        if (spec != null) {
            if (spec.getRules() != null) {
                spec.getRules().forEach(rule -> {
                    if (rule.getHost() != null) {
                        ruleHosts.add(rule.getHost());
                    }
                });
            }
            if (spec.getTls() != null) {
                for (V1IngressTLS tls : spec.getTls()) {
                    if (tls.getHosts() != null) {
                        tlsHosts.addAll(tls.getHosts());
                    }
                }
            }
        }

        return new IngressMapper.IngressView(name, namespace, ingressClass, annotations, ruleHosts, tlsHosts);
    }
}
