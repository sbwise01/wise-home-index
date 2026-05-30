package com.bradandmarsha.wisehomeindex.service;

import com.bradandmarsha.wisehomeindex.config.ConfigLoader;
import com.bradandmarsha.wisehomeindex.model.ApplicationEntry;
import com.bradandmarsha.wisehomeindex.model.IndexConfig;

import java.util.List;

/**
 * Holds the loaded configuration and filters applications according to the
 * visibility scope determined from the request context.
 */
public class IndexService {

    private final IndexConfig config;

    public IndexService() {
        this(ConfigLoader.load());
    }

    public IndexService(IndexConfig config) {
        this.config = config;
    }

    /**
     * Returns the applications a caller is allowed to see.
     *
     * @param includePrivate whether private URLs should be included (trusted subnet)
     * @return the filtered, immutable list of applications
     */
    public List<ApplicationEntry> applicationsFor(boolean includePrivate) {
        if (includePrivate) {
            return List.copyOf(config.getApplications());
        }
        return config.getApplications().stream()
                .filter(ApplicationEntry::isPublic)
                .toList();
    }

    /**
     * @return all configured applications regardless of visibility
     */
    public List<ApplicationEntry> allApplications() {
        return List.copyOf(config.getApplications());
    }
}
