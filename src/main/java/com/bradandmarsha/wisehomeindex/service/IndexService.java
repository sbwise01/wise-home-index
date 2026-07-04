package com.bradandmarsha.wisehomeindex.service;

import com.bradandmarsha.wisehomeindex.discovery.ApplicationSource;
import com.bradandmarsha.wisehomeindex.model.ApplicationEntry;

import java.util.List;

/**
 * Provides the applications to display, filtered according to the visibility
 * scope determined from the request context.
 *
 * <p>The underlying data is supplied by an {@link ApplicationSource} (discovered
 * from Kubernetes {@code Ingress} resources in production) and is already ordered
 * for presentation; filtering preserves that order.</p>
 */
public class IndexService {

    private final ApplicationSource source;

    public IndexService(ApplicationSource source) {
        this.source = source;
    }

    /**
     * Returns the applications a caller is allowed to see.
     *
     * @param includePrivate whether private applications should be included
     *                       (trusted request origin)
     * @return the filtered, immutable list of applications
     */
    public List<ApplicationEntry> applicationsFor(boolean includePrivate) {
        List<ApplicationEntry> all = source.getApplications();
        if (includePrivate) {
            return List.copyOf(all);
        }
        return all.stream()
                .filter(ApplicationEntry::isPublic)
                .toList();
    }

    /**
     * @return all discovered applications regardless of visibility
     */
    public List<ApplicationEntry> allApplications() {
        return List.copyOf(source.getApplications());
    }
}
