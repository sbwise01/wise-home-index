package com.bradandmarsha.wisehomeindex.util;

import jakarta.servlet.http.HttpServletRequest;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Helpers for classifying the origin of an HTTP request.
 *
 * <p>Requests originating from a trusted source are allowed to see both public
 * and private URLs. Everything else is treated as an internet-based source and
 * only sees public URLs.</p>
 *
 * <p>A request is trusted when its client IP either:</p>
 * <ul>
 *   <li>falls within the trusted private subnet (see {@link #PRIVATE_NETWORK_CIDR}), or</li>
 *   <li>matches a current public IP of the home host (see {@link #PUBLIC_HOST}).</li>
 * </ul>
 *
 * <p>The trusted subnet defaults to {@value #DEFAULT_PRIVATE_NETWORK_CIDR} but
 * can be overridden at startup via the system property
 * {@value #SYSTEM_PROPERTY} or the environment variable {@value #ENV_VARIABLE}
 * (the system property takes precedence). The value must be IPv4 CIDR notation,
 * e.g. {@code 10.0.0.0/8}. An invalid value falls back to the default.</p>
 *
 * <p>The home host defaults to {@value #DEFAULT_PUBLIC_HOST} and can be
 * overridden via the system property {@value #PUBLIC_HOST_SYSTEM_PROPERTY} or
 * the environment variable {@value #PUBLIC_HOST_ENV_VARIABLE}. Because that
 * host typically uses dynamic DNS, its IP addresses are re-resolved on demand
 * and cached for {@value #PUBLIC_IP_TTL_MILLIS} ms. Only IPv4 addresses are
 * considered.</p>
 */
public final class NetworkUtil {

    private static final Logger LOG = Logger.getLogger(NetworkUtil.class.getName());

    /** System property used to override the trusted private subnet. */
    public static final String SYSTEM_PROPERTY = "wise.home.index.private.cidr";
    /** Environment variable used to override the trusted private subnet. */
    public static final String ENV_VARIABLE = "WISE_HOME_INDEX_PRIVATE_CIDR";
    /** Default trusted private subnet when nothing is configured. */
    public static final String DEFAULT_PRIVATE_NETWORK_CIDR = "192.168.0.0/24";

    /** System property used to override the trusted home host. */
    public static final String PUBLIC_HOST_SYSTEM_PROPERTY = "wise.home.index.public.host";
    /** Environment variable used to override the trusted home host. */
    public static final String PUBLIC_HOST_ENV_VARIABLE = "WISE_HOME_INDEX_PUBLIC_HOST";
    /** Default trusted home host when nothing is configured. */
    public static final String DEFAULT_PUBLIC_HOST = "home.bradandmarsha.com";

    /** How long resolved public host IPs are cached before being re-resolved. */
    public static final long PUBLIC_IP_TTL_MILLIS = 5 * 60 * 1000L;

    /** The trusted private subnet that is allowed to see private URLs (resolved at load time). */
    public static final String PRIVATE_NETWORK_CIDR;

    /** The trusted home host whose current public IP(s) are allowed to see private URLs. */
    public static final String PUBLIC_HOST;

    private static final int PRIVATE_NETWORK;
    private static final int PRIVATE_MASK;

    private static volatile Set<Integer> cachedPublicIps = Set.of();
    private static volatile long publicIpsExpireAt = 0L;

    static {
        String configured = resolveConfiguredCidr();
        String cidr;
        int[] parsed;
        if (configured != null && !configured.isBlank()) {
            String trimmed = configured.trim();
            try {
                parsed = parseCidr(trimmed);
                cidr = trimmed;
            } catch (IllegalArgumentException ex) {
                LOG.warning(() -> "Invalid private network CIDR '" + trimmed + "' ("
                        + ex.getMessage() + "); falling back to default " + DEFAULT_PRIVATE_NETWORK_CIDR);
                cidr = DEFAULT_PRIVATE_NETWORK_CIDR;
                parsed = parseCidr(DEFAULT_PRIVATE_NETWORK_CIDR);
            }
        } else {
            cidr = DEFAULT_PRIVATE_NETWORK_CIDR;
            parsed = parseCidr(DEFAULT_PRIVATE_NETWORK_CIDR);
        }
        PRIVATE_NETWORK_CIDR = cidr;
        PRIVATE_MASK = parsed[1];
        PRIVATE_NETWORK = parsed[0] & parsed[1];

        PUBLIC_HOST = resolveConfiguredPublicHost();

        LOG.info(() -> "Trusted private network configured as " + PRIVATE_NETWORK_CIDR
                + "; trusted home host configured as " + PUBLIC_HOST);
    }

    private NetworkUtil() {
    }

    private static String resolveConfiguredCidr() {
        String fromProperty = System.getProperty(SYSTEM_PROPERTY);
        if (fromProperty != null && !fromProperty.isBlank()) {
            return fromProperty;
        }
        return System.getenv(ENV_VARIABLE);
    }

    private static String resolveConfiguredPublicHost() {
        String fromProperty = System.getProperty(PUBLIC_HOST_SYSTEM_PROPERTY);
        if (fromProperty != null && !fromProperty.isBlank()) {
            return fromProperty.trim();
        }
        String fromEnv = System.getenv(PUBLIC_HOST_ENV_VARIABLE);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv.trim();
        }
        return DEFAULT_PUBLIC_HOST;
    }

    /**
     * Determines the client IP address for a request, honoring the
     * {@code X-Forwarded-For} header set by upstream proxies / ingress controllers.
     *
     * @param request the incoming request
     * @return the best-effort client IP address, or {@code null} if none can be determined
     */
    public static String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // X-Forwarded-For may contain a comma-separated list; the first entry is the origin client.
            return forwarded.split(",", 2)[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * @param ip an IPv4 (or IPv4-mapped) address string
     * @return {@code true} if the address falls within the configured private subnet
     *         (see {@link #PRIVATE_NETWORK_CIDR})
     */
    public static boolean isPrivateNetwork(String ip) {
        Integer parsed = parseIpv4(ip);
        if (parsed == null) {
            return false;
        }
        return (parsed & PRIVATE_MASK) == PRIVATE_NETWORK;
    }

    /**
     * @param ip an IPv4 (or IPv4-mapped) address string
     * @return {@code true} if the address matches a current public IP of the
     *         trusted home host (see {@link #PUBLIC_HOST})
     */
    public static boolean isPublicHostIp(String ip) {
        return matchesPublicHostIp(ip, publicHostIps());
    }

    /**
     * Determines whether a request should be trusted to see private URLs.
     *
     * @param request the incoming request
     * @return {@code true} if the request originates from the trusted private
     *         subnet or from a current public IP of the trusted home host
     */
    public static boolean isPrivateRequest(HttpServletRequest request) {
        String clientIp = resolveClientIp(request);
        return isPrivateNetwork(clientIp) || isPublicHostIp(clientIp);
    }

    /**
     * @return the currently cached public IP(s) of the trusted home host, in
     *         dotted-quad form (re-resolving if the cache has expired)
     */
    public static List<String> resolvedPublicIps() {
        return publicHostIps().stream().map(NetworkUtil::ipToString).toList();
    }

    /**
     * Matching core, exposed for testing with an injected trusted-IP set so the
     * logic can be verified without performing real DNS resolution.
     *
     * @param ip        the candidate IP string
     * @param publicIps the set of trusted public IPs (as packed ints)
     * @return {@code true} if {@code ip} parses to one of {@code publicIps}
     */
    public static boolean matchesPublicHostIp(String ip, Set<Integer> publicIps) {
        Integer parsed = parseIpv4(ip);
        if (parsed == null || publicIps == null || publicIps.isEmpty()) {
            return false;
        }
        return publicIps.contains(parsed);
    }

    private static Set<Integer> publicHostIps() {
        long now = System.currentTimeMillis();
        if (now < publicIpsExpireAt) {
            return cachedPublicIps;
        }
        Set<Integer> resolved = new LinkedHashSet<>();
        try {
            for (InetAddress addr : InetAddress.getAllByName(PUBLIC_HOST)) {
                Integer ip = parseIpv4(addr.getHostAddress());
                if (ip != null) {
                    resolved.add(ip);
                }
            }
            cachedPublicIps = Set.copyOf(resolved);
        } catch (UnknownHostException ex) {
            // Keep serving with the last-known IPs rather than losing trust on a transient DNS failure.
            LOG.warning(() -> "Unable to resolve trusted home host '" + PUBLIC_HOST + "': " + ex.getMessage());
        }
        publicIpsExpireAt = now + PUBLIC_IP_TTL_MILLIS;
        return cachedPublicIps;
    }

    private static String ipToString(int ip) {
        return ((ip >> 24) & 0xFF) + "."
                + ((ip >> 16) & 0xFF) + "."
                + ((ip >> 8) & 0xFF) + "."
                + (ip & 0xFF);
    }

    private static Integer parseIpv4(String ip) {
        if (ip == null || ip.isBlank()) {
            return null;
        }
        String candidate = ip.trim();

        // Normalize IPv4-mapped IPv6 forms such as ::ffff:192.168.0.5
        int lastColon = candidate.lastIndexOf(':');
        if (lastColon >= 0 && candidate.indexOf('.') > lastColon) {
            candidate = candidate.substring(lastColon + 1);
        }

        // Strip a zone identifier if present (e.g. for link-local addresses).
        int percent = candidate.indexOf('%');
        if (percent >= 0) {
            candidate = candidate.substring(0, percent);
        }

        String[] octets = candidate.split("\\.");
        if (octets.length != 4) {
            return null;
        }
        int value = 0;
        for (String octet : octets) {
            int part;
            try {
                part = Integer.parseInt(octet);
            } catch (NumberFormatException ex) {
                return null;
            }
            if (part < 0 || part > 255) {
                return null;
            }
            value = (value << 8) | part;
        }
        return value;
    }

    /**
     * Parses IPv4 CIDR notation ({@code a.b.c.d/prefix}) into a network address
     * and subnet mask.
     *
     * @param cidr the CIDR string
     * @return a two-element array: {@code [networkAddress, mask]}
     * @throws IllegalArgumentException if the CIDR is malformed
     */
    private static int[] parseCidr(String cidr) {
        String[] parts = cidr.split("/");
        if (parts.length != 2) {
            throw new IllegalArgumentException("expected a.b.c.d/prefix");
        }
        Integer network = parseIpv4(parts[0]);
        if (network == null) {
            throw new IllegalArgumentException("invalid network address");
        }
        int prefix;
        try {
            prefix = Integer.parseInt(parts[1].trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("invalid prefix length");
        }
        if (prefix < 0 || prefix > 32) {
            throw new IllegalArgumentException("prefix length must be between 0 and 32");
        }
        int mask = prefix == 0 ? 0 : 0xFFFFFFFF << (32 - prefix);
        return new int[]{network, mask};
    }
}
