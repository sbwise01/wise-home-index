package com.bradandmarsha.wisehomeindex.discovery;

import com.bradandmarsha.wisehomeindex.model.ApplicationEntry;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Pure mapping logic that turns the relevant fields of a Gateway API
 * {@code HTTPRoute} into an {@link ApplicationEntry}, based on its
 * {@code index.home.bradandmarsha.com/*} annotations and parent Gateway refs.
 *
 * <p>Kept free of the Kubernetes client types so it can be unit-tested without a
 * live cluster; {@link HttpRouteApplicationSource} adapts API objects into an
 * {@link HttpRouteView}.</p>
 */
public final class HttpRouteMapper {

    private static final Logger LOG = Logger.getLogger(HttpRouteMapper.class.getName());

    /** Weight assigned to entries whose annotation is missing or invalid (sorted last). */
    public static final int DEFAULT_WEIGHT = IngressMapper.DEFAULT_WEIGHT;

    private final DiscoverySettings settings;

    public HttpRouteMapper(DiscoverySettings settings) {
        this.settings = settings;
    }

    /**
     * A minimal, client-agnostic view of the HTTPRoute fields needed for mapping.
     *
     * @param name              {@code metadata.name} (logging)
     * @param namespace         {@code metadata.namespace} (logging)
     * @param annotations       {@code metadata.annotations} (may be {@code null})
     * @param hostnames         {@code spec.hostnames} (may be empty)
     * @param parentGatewayNames {@code spec.parentRefs[].name} values
     * @param parentSectionNames {@code spec.parentRefs[].sectionName} values (may include nulls)
     */
    public record HttpRouteView(String name,
                                String namespace,
                                Map<String, String> annotations,
                                List<String> hostnames,
                                List<String> parentGatewayNames,
                                List<String> parentSectionNames) {
    }

    /**
     * Maps an HTTPRoute view to an {@link ApplicationEntry}, or empty when the
     * route is not opted in or cannot be represented.
     */
    public Optional<ApplicationEntry> map(HttpRouteView view) {
        Map<String, String> annotations = view.annotations() != null ? view.annotations() : Map.of();
        String ref = describe(view);

        if (!isTrue(annotations.get(settings.enabledAnnotation()))) {
            return Optional.empty();
        }

        String name = trimToNull(annotations.get(settings.nameAnnotation()));
        if (name == null) {
            LOG.warning(() -> "HTTPRoute " + ref + " is index-enabled but has no '"
                    + settings.nameAnnotation() + "' annotation; skipping");
            return Optional.empty();
        }

        String host = firstNonBlank(view.hostnames());
        if (host == null) {
            LOG.warning(() -> "HTTPRoute " + ref + " ('" + name + "') has no usable host in spec.hostnames; skipping");
            return Optional.empty();
        }

        boolean https = prefersHttps(view.parentSectionNames());
        String url = (https ? "https://" : "http://") + host;

        String image = trimToNull(annotations.get(settings.imageAnnotation()));
        String description = trimToNull(annotations.get(settings.descriptionAnnotation()));
        int weight = parseWeight(annotations.get(settings.weightAnnotation()), ref);
        boolean publicApp = resolveVisibility(view.parentGatewayNames(), ref);

        return Optional.of(new ApplicationEntry(name, url, image, description, weight, publicApp));
    }

    private boolean resolveVisibility(List<String> parentGatewayNames, String ref) {
        boolean sawPublic = false;
        boolean sawPrivate = false;
        if (parentGatewayNames != null) {
            for (String raw : parentGatewayNames) {
                String name = trimToNull(raw);
                if (name == null) {
                    continue;
                }
                if (name.equalsIgnoreCase(settings.getPublicGatewayName())) {
                    sawPublic = true;
                } else if (name.equalsIgnoreCase(settings.getPrivateGatewayName())) {
                    sawPrivate = true;
                }
            }
        }
        if (sawPublic) {
            return true;
        }
        if (sawPrivate) {
            return false;
        }
        LOG.warning(() -> "HTTPRoute " + ref + " has no parent Gateway named '"
                + settings.getPublicGatewayName() + "' or '"
                + settings.getPrivateGatewayName() + "'; treating as private");
        return false;
    }

    /**
     * Prefer https when any parent listener section looks like HTTPS; otherwise
     * http. Homelab canaries attach the indexed route to an {@code https-*} listener.
     */
    private static boolean prefersHttps(List<String> sectionNames) {
        if (sectionNames == null || sectionNames.isEmpty()) {
            return true;
        }
        boolean sawHttp = false;
        for (String raw : sectionNames) {
            String section = trimToNull(raw);
            if (section == null) {
                continue;
            }
            String lower = section.toLowerCase(Locale.ROOT);
            if (lower.startsWith("https")) {
                return true;
            }
            if (lower.equals("http") || lower.startsWith("http-")) {
                sawHttp = true;
            }
        }
        return !sawHttp;
    }

    private int parseWeight(String raw, String ref) {
        String value = trimToNull(raw);
        if (value == null) {
            return DEFAULT_WEIGHT;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            LOG.warning(() -> "HTTPRoute " + ref + " has invalid weight '" + raw
                    + "'; using default " + DEFAULT_WEIGHT);
            return DEFAULT_WEIGHT;
        }
    }

    private static boolean isTrue(String value) {
        return value != null && value.trim().equalsIgnoreCase("true");
    }

    private static String firstNonBlank(List<String> values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String trimmed = trimToNull(value);
            if (trimmed != null) {
                return trimmed;
            }
        }
        return null;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String describe(HttpRouteView view) {
        String ns = view.namespace() != null ? view.namespace() : "?";
        String nm = view.name() != null ? view.name() : "?";
        return ns + "/" + nm;
    }
}
