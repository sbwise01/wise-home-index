package com.bradandmarsha.wisehomeindex.discovery;

import com.bradandmarsha.wisehomeindex.model.ApplicationEntry;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Pure mapping logic that turns the relevant fields of a Kubernetes
 * {@code Ingress} into an {@link ApplicationEntry}, based on its
 * {@code index.home.bradandmarsha.com/*} annotations and its ingress class.
 *
 * <p>Kept free of the Kubernetes client types so it can be unit-tested without a
 * live cluster; {@link IngressApplicationSource} adapts real {@code V1Ingress}
 * objects into an {@link IngressView}.</p>
 */
public final class IngressMapper {

    private static final Logger LOG = Logger.getLogger(IngressMapper.class.getName());

    /** Weight assigned to entries whose annotation is missing or invalid (sorted last). */
    public static final int DEFAULT_WEIGHT = 1000;

    private final DiscoverySettings settings;

    public IngressMapper(DiscoverySettings settings) {
        this.settings = settings;
    }

    /**
     * A minimal, client-agnostic view of the ingress fields needed for mapping.
     *
     * @param name        the ingress {@code metadata.name} (used only for logging)
     * @param namespace   the ingress {@code metadata.namespace} (used only for logging)
     * @param ingressClass the resolved ingress class name (may be {@code null})
     * @param annotations the ingress annotations (may be {@code null})
     * @param ruleHosts   hosts from {@code spec.rules[].host} (may be empty)
     * @param tlsHosts    hosts from {@code spec.tls[].hosts[]} (may be empty)
     */
    public record IngressView(String name,
                              String namespace,
                              String ingressClass,
                              Map<String, String> annotations,
                              List<String> ruleHosts,
                              List<String> tlsHosts) {
    }

    /**
     * Maps an ingress view to an {@link ApplicationEntry}, or returns empty when
     * the ingress is not opted in or cannot be represented (missing name/host).
     *
     * @param view the ingress fields
     * @return the mapped application, or {@link Optional#empty()}
     */
    public Optional<ApplicationEntry> map(IngressView view) {
        Map<String, String> annotations = view.annotations() != null ? view.annotations() : Map.of();
        String ref = describe(view);

        if (!isTrue(annotations.get(settings.enabledAnnotation()))) {
            return Optional.empty();
        }

        String name = trimToNull(annotations.get(settings.nameAnnotation()));
        if (name == null) {
            LOG.warning(() -> "Ingress " + ref + " is index-enabled but has no '"
                    + settings.nameAnnotation() + "' annotation; skipping");
            return Optional.empty();
        }

        String host = firstNonBlank(view.ruleHosts());
        boolean hasTls = view.tlsHosts() != null && !view.tlsHosts().isEmpty();
        if (host == null) {
            host = firstNonBlank(view.tlsHosts());
        }
        if (host == null) {
            LOG.warning(() -> "Ingress " + ref + " ('" + name + "') has no usable host in spec.rules/spec.tls; skipping");
            return Optional.empty();
        }
        String url = (hasTls ? "https://" : "http://") + host;

        String image = trimToNull(annotations.get(settings.imageAnnotation()));
        String description = trimToNull(annotations.get(settings.descriptionAnnotation()));
        int weight = parseWeight(annotations.get(settings.weightAnnotation()), ref);
        boolean publicApp = resolveVisibility(view.ingressClass(), ref);

        return Optional.of(new ApplicationEntry(name, url, image, description, weight, publicApp));
    }

    private boolean resolveVisibility(String ingressClass, String ref) {
        String cls = trimToNull(ingressClass);
        if (cls != null && cls.equalsIgnoreCase(settings.getPublicIngressClass())) {
            return true;
        }
        if (cls != null && cls.equalsIgnoreCase(settings.getPrivateIngressClass())) {
            return false;
        }
        // Unknown/absent class: default to private so it is never exposed publicly.
        LOG.warning(() -> "Ingress " + ref + " has unrecognized ingress class '" + ingressClass
                + "' (expected '" + settings.getPublicIngressClass() + "' or '"
                + settings.getPrivateIngressClass() + "'); treating as private");
        return false;
    }

    private int parseWeight(String raw, String ref) {
        String value = trimToNull(raw);
        if (value == null) {
            return DEFAULT_WEIGHT;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            LOG.warning(() -> "Ingress " + ref + " has invalid weight '" + raw
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

    private static String describe(IngressView view) {
        String ns = view.namespace() != null ? view.namespace() : "?";
        String nm = view.name() != null ? view.name() : "?";
        return ns + "/" + nm;
    }
}
