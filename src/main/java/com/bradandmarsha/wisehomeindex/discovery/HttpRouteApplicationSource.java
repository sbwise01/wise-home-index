package com.bradandmarsha.wisehomeindex.discovery;

import com.bradandmarsha.wisehomeindex.model.ApplicationEntry;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CustomObjectsApi;
import io.kubernetes.client.util.ClientBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * {@link ApplicationSource} backed by Gateway API {@code HTTPRoute} resources
 * listed cluster-wide via the generic CustomObjects API.
 *
 * <p>Caching and failure behavior match {@link IngressApplicationSource}: TTL
 * refresh, retain last-known list on errors.</p>
 */
public class HttpRouteApplicationSource implements ApplicationSource {

    private static final Logger LOG = Logger.getLogger(HttpRouteApplicationSource.class.getName());

    private static final String GROUP = "gateway.networking.k8s.io";
    private static final String VERSION = "v1";
    private static final String PLURAL = "httproutes";

    private static final Comparator<ApplicationEntry> DISPLAY_ORDER =
            Comparator.comparingInt(ApplicationEntry::getWeight)
                    .thenComparing(e -> e.getName() == null ? "" : e.getName(), String.CASE_INSENSITIVE_ORDER);

    private final DiscoverySettings settings;
    private final HttpRouteMapper mapper;
    private final long refreshMillis;

    private volatile CustomObjectsApi api;
    private volatile List<ApplicationEntry> cache = List.of();
    private volatile long expiresAt = 0L;
    private volatile boolean loadedAtLeastOnce = false;

    public HttpRouteApplicationSource(DiscoverySettings settings) {
        this.settings = settings;
        this.mapper = new HttpRouteMapper(settings);
        this.refreshMillis = settings.getRefreshInterval().toMillis();
    }

    /** Test seam: inject a pre-built API instead of building one from cluster config. */
    HttpRouteApplicationSource(DiscoverySettings settings, CustomObjectsApi api) {
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
        if (loadedAtLeastOnce && System.currentTimeMillis() < expiresAt) {
            return cache;
        }

        try {
            CustomObjectsApi client = api();
            Object response = client.listClusterCustomObject(GROUP, VERSION, PLURAL).execute();
            List<HttpRouteMapper.HttpRouteView> views = toViews(response);
            List<ApplicationEntry> discovered = views.stream()
                    .map(mapper::map)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .sorted(DISPLAY_ORDER)
                    .collect(Collectors.toList());
            cache = List.copyOf(discovered);
            loadedAtLeastOnce = true;
            LOG.info(() -> "Discovered " + cache.size() + " application(s) from HTTPRoute resources");
        } catch (ApiException ex) {
            LOG.log(Level.WARNING, ex, () -> "Failed to list HTTPRoute resources (HTTP " + ex.getCode()
                    + "): " + ex.getResponseBody() + "; keeping last-known "
                    + (loadedAtLeastOnce ? cache.size() + " application(s)" : "empty list"));
        } catch (IOException ex) {
            LOG.log(Level.WARNING, ex, () -> "Unable to initialize Kubernetes client for HTTPRoutes; keeping last-known "
                    + (loadedAtLeastOnce ? cache.size() + " application(s)" : "empty list"));
        } catch (RuntimeException ex) {
            LOG.log(Level.WARNING, ex, () -> "Unexpected error during HTTPRoute discovery; keeping last-known "
                    + (loadedAtLeastOnce ? cache.size() + " application(s)" : "empty list"));
        }

        expiresAt = System.currentTimeMillis() + refreshMillis;
        return cache;
    }

    private CustomObjectsApi api() throws IOException {
        CustomObjectsApi existing = api;
        if (existing != null) {
            return existing;
        }
        ApiClient client = ClientBuilder.standard().build();
        CustomObjectsApi built = new CustomObjectsApi(client);
        api = built;
        return built;
    }

    /** Visible for unit tests that feed a CustomObjects list payload. */
    @SuppressWarnings("unchecked")
    public static List<HttpRouteMapper.HttpRouteView> toViews(Object response) {
        if (!(response instanceof Map<?, ?> root)) {
            return List.of();
        }
        Object itemsObj = root.get("items");
        if (!(itemsObj instanceof List<?> items)) {
            return List.of();
        }
        List<HttpRouteMapper.HttpRouteView> views = new ArrayList<>();
        for (Object itemObj : items) {
            if (!(itemObj instanceof Map<?, ?> item)) {
                continue;
            }
            Map<String, Object> meta = asStringKeyedMap(item.get("metadata"));
            Map<String, Object> spec = asStringKeyedMap(item.get("spec"));

            String name = stringOrNull(meta.get("name"));
            String namespace = stringOrNull(meta.get("namespace"));
            Map<String, String> annotations = stringMap(meta.get("annotations"));

            List<String> hostnames = stringList(spec.get("hostnames"));
            List<String> parentNames = new ArrayList<>();
            List<String> sectionNames = new ArrayList<>();
            Object parentRefsObj = spec.get("parentRefs");
            if (parentRefsObj instanceof List<?> parentRefs) {
                for (Object refObj : parentRefs) {
                    Map<String, Object> ref = asStringKeyedMap(refObj);
                    parentNames.add(stringOrNull(ref.get("name")));
                    sectionNames.add(stringOrNull(ref.get("sectionName")));
                }
            }

            views.add(new HttpRouteMapper.HttpRouteView(
                    name, namespace, annotations, hostnames, parentNames, sectionNames));
        }
        return views;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asStringKeyedMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : map.entrySet()) {
            if (e.getKey() != null) {
                out.put(String.valueOf(e.getKey()), e.getValue());
            }
        }
        return out;
    }

    private static Map<String, String> stringMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return null;
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : map.entrySet()) {
            if (e.getKey() != null && e.getValue() != null) {
                out.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
            }
        }
        return Collections.unmodifiableMap(out);
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object item : list) {
            if (item != null) {
                out.add(String.valueOf(item));
            }
        }
        return out;
    }

    private static String stringOrNull(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
