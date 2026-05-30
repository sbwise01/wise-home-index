package com.bradandmarsha.wisehomeindex.model;

import java.net.URI;

/**
 * A single application to display on the index page.
 *
 * <p>Backed by the YAML configuration. {@code name} and {@code url} are required;
 * {@code image} is optional and falls back to a default tile image when absent.</p>
 */
public class ApplicationEntry {

    private String name;
    private String url;
    private String image;

    public ApplicationEntry() {
    }

    public ApplicationEntry(String name, String url, String image) {
        this.name = name;
        this.url = url;
        this.image = image;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    /**
     * Public applications are always sub-domains of {@code home.bradandmarsha.com}.
     * Everything else (short names like {@code ceph-dashboard}) is treated as private.
     *
     * @return {@code true} when this entry resolves to a public, internet-facing URL
     */
    public boolean isPublic() {
        String host = extractHost(url);
        if (host == null) {
            return false;
        }
        host = host.toLowerCase();
        return host.equals(PUBLIC_DOMAIN) || host.endsWith("." + PUBLIC_DOMAIN);
    }

    private static final String PUBLIC_DOMAIN = "home.bradandmarsha.com";

    private static String extractHost(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String candidate = value.trim();
        try {
            if (candidate.contains("://")) {
                return URI.create(candidate).getHost();
            }
            // No scheme: parse it as an authority by prepending one.
            URI uri = URI.create("//" + candidate);
            String host = uri.getHost();
            return host != null ? host : candidate.split("[/?#]", 2)[0];
        } catch (IllegalArgumentException ex) {
            return candidate.split("[/?#]", 2)[0];
        }
    }
}
