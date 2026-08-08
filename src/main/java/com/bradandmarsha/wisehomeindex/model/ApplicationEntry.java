package com.bradandmarsha.wisehomeindex.model;

/**
 * A single application to display on the index page.
 *
 * <p>Instances are discovered from Kubernetes {@code Ingress} and/or Gateway API
 * {@code HTTPRoute} resources. Each field is populated from the
 * {@code index.home.bradandmarsha.com/*} annotations, except for {@code url}
 * (derived from the host) and public/private visibility (ingress class or
 * parent Gateway name).</p>
 *
 * <p>{@code name} and {@code url} are always present; {@code image} and
 * {@code description} are optional. {@code weight} controls display ordering
 * (ascending, lower first).</p>
 */
public class ApplicationEntry {

    private final String name;
    private final String url;
    private final String image;
    private final String description;
    private final int weight;
    private final boolean publicApp;

    public ApplicationEntry(String name, String url, String image, String description, int weight, boolean publicApp) {
        this.name = name;
        this.url = url;
        this.image = image;
        this.description = description;
        this.weight = weight;
        this.publicApp = publicApp;
    }

    public String getName() {
        return name;
    }

    public String getUrl() {
        return url;
    }

    public String getImage() {
        return image;
    }

    public String getDescription() {
        return description;
    }

    public int getWeight() {
        return weight;
    }

    /**
     * Whether this application is publicly visible. For Ingress: public class
     * (e.g. {@code nginx}) → {@code true}, private class (e.g.
     * {@code nginx-internal}) → {@code false}. For HTTPRoute: parent Gateway
     * {@code gateway-public} → {@code true}, {@code gateway-internal} →
     * {@code false}.
     *
     * @return {@code true} when this entry should be shown to internet callers
     */
    public boolean isPublic() {
        return publicApp;
    }
}
