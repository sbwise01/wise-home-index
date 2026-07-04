package com.bradandmarsha.wisehomeindex.discovery;

import java.time.Duration;
import java.util.logging.Logger;

/**
 * Configuration for discovering applications from Kubernetes {@code Ingress}
 * resources.
 *
 * <p>All values have sensible defaults and can be overridden (in this order of
 * precedence) by a system property, then an environment variable:</p>
 *
 * <table>
 *   <caption>Configuration keys</caption>
 *   <tr><th>Setting</th><th>System property</th><th>Environment variable</th><th>Default</th></tr>
 *   <tr><td>Annotation prefix</td><td>{@code wise.home.index.annotation.prefix}</td>
 *       <td>{@code WISE_HOME_INDEX_ANNOTATION_PREFIX}</td><td>{@code index.home.bradandmarsha.com}</td></tr>
 *   <tr><td>Public ingress class</td><td>{@code wise.home.index.ingress.class.public}</td>
 *       <td>{@code WISE_HOME_INDEX_INGRESS_CLASS_PUBLIC}</td><td>{@code nginx}</td></tr>
 *   <tr><td>Private ingress class</td><td>{@code wise.home.index.ingress.class.private}</td>
 *       <td>{@code WISE_HOME_INDEX_INGRESS_CLASS_PRIVATE}</td><td>{@code nginx-internal}</td></tr>
 *   <tr><td>Refresh interval (seconds)</td><td>{@code wise.home.index.refresh.seconds}</td>
 *       <td>{@code WISE_HOME_INDEX_REFRESH_SECONDS}</td><td>{@code 30}</td></tr>
 * </table>
 *
 * <p>The annotation suffixes are fixed: {@code /enabled}, {@code /name},
 * {@code /image}, {@code /description}, {@code /weight}.</p>
 */
public final class DiscoverySettings {

    private static final Logger LOG = Logger.getLogger(DiscoverySettings.class.getName());

    public static final String PREFIX_PROPERTY = "wise.home.index.annotation.prefix";
    public static final String PREFIX_ENV = "WISE_HOME_INDEX_ANNOTATION_PREFIX";
    public static final String DEFAULT_PREFIX = "index.home.bradandmarsha.com";

    public static final String PUBLIC_CLASS_PROPERTY = "wise.home.index.ingress.class.public";
    public static final String PUBLIC_CLASS_ENV = "WISE_HOME_INDEX_INGRESS_CLASS_PUBLIC";
    public static final String DEFAULT_PUBLIC_CLASS = "nginx";

    public static final String PRIVATE_CLASS_PROPERTY = "wise.home.index.ingress.class.private";
    public static final String PRIVATE_CLASS_ENV = "WISE_HOME_INDEX_INGRESS_CLASS_PRIVATE";
    public static final String DEFAULT_PRIVATE_CLASS = "nginx-internal";

    public static final String REFRESH_PROPERTY = "wise.home.index.refresh.seconds";
    public static final String REFRESH_ENV = "WISE_HOME_INDEX_REFRESH_SECONDS";
    public static final long DEFAULT_REFRESH_SECONDS = 300L;

    private final String annotationPrefix;
    private final String publicIngressClass;
    private final String privateIngressClass;
    private final Duration refreshInterval;

    public DiscoverySettings(String annotationPrefix,
                             String publicIngressClass,
                             String privateIngressClass,
                             Duration refreshInterval) {
        this.annotationPrefix = annotationPrefix;
        this.publicIngressClass = publicIngressClass;
        this.privateIngressClass = privateIngressClass;
        this.refreshInterval = refreshInterval;
    }

    /**
     * @return settings resolved from system properties / environment variables,
     *         falling back to the documented defaults
     */
    public static DiscoverySettings fromEnvironment() {
        String prefix = resolve(PREFIX_PROPERTY, PREFIX_ENV, DEFAULT_PREFIX);
        String publicClass = resolve(PUBLIC_CLASS_PROPERTY, PUBLIC_CLASS_ENV, DEFAULT_PUBLIC_CLASS);
        String privateClass = resolve(PRIVATE_CLASS_PROPERTY, PRIVATE_CLASS_ENV, DEFAULT_PRIVATE_CLASS);
        Duration refresh = resolveRefresh();

        LOG.info(() -> "Ingress discovery configured: prefix=" + prefix
                + ", publicClass=" + publicClass
                + ", privateClass=" + privateClass
                + ", refresh=" + refresh.toSeconds() + "s");
        return new DiscoverySettings(prefix, publicClass, privateClass, refresh);
    }

    public String getAnnotationPrefix() {
        return annotationPrefix;
    }

    public String getPublicIngressClass() {
        return publicIngressClass;
    }

    public String getPrivateIngressClass() {
        return privateIngressClass;
    }

    public Duration getRefreshInterval() {
        return refreshInterval;
    }

    public String enabledAnnotation() {
        return annotationPrefix + "/enabled";
    }

    public String nameAnnotation() {
        return annotationPrefix + "/name";
    }

    public String imageAnnotation() {
        return annotationPrefix + "/image";
    }

    public String descriptionAnnotation() {
        return annotationPrefix + "/description";
    }

    public String weightAnnotation() {
        return annotationPrefix + "/weight";
    }

    private static String resolve(String property, String env, String fallback) {
        String fromProperty = System.getProperty(property);
        if (fromProperty != null && !fromProperty.isBlank()) {
            return fromProperty.trim();
        }
        String fromEnv = System.getenv(env);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv.trim();
        }
        return fallback;
    }

    private static Duration resolveRefresh() {
        String raw = resolve(REFRESH_PROPERTY, REFRESH_ENV, Long.toString(DEFAULT_REFRESH_SECONDS));
        try {
            long seconds = Long.parseLong(raw.trim());
            if (seconds < 0) {
                throw new NumberFormatException("must be >= 0");
            }
            return Duration.ofSeconds(seconds);
        } catch (NumberFormatException ex) {
            LOG.warning(() -> "Invalid refresh interval '" + raw + "' (" + ex.getMessage()
                    + "); falling back to default " + DEFAULT_REFRESH_SECONDS + "s");
            return Duration.ofSeconds(DEFAULT_REFRESH_SECONDS);
        }
    }
}
