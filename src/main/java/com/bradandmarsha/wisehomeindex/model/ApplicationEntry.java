package com.bradandmarsha.wisehomeindex.model;

/**
 * A single application to display on the index page.
 *
 * <p>Instances are discovered from Kubernetes {@code Ingress} resources. Each
 * field is populated from the {@code index.home.bradandmarsha.com/*} annotations
 * on the ingress, except for {@code url} (derived from the ingress host) and the
 * public/private visibility (derived from the ingress class).</p>
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
     * Whether this application is publicly visible. Determined by the ingress
     * class of the originating {@code Ingress}: the public class (e.g.
     * {@code nginx}) maps to {@code true}, and the private/internal class (e.g.
     * {@code nginx-internal}) maps to {@code false}.
     *
     * @return {@code true} when this entry should be shown to internet callers
     */
    public boolean isPublic() {
        return publicApp;
    }
}
