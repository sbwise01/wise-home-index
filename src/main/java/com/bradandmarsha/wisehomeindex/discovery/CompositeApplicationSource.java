package com.bradandmarsha.wisehomeindex.discovery;

import com.bradandmarsha.wisehomeindex.model.ApplicationEntry;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Merges applications from multiple {@link ApplicationSource}s and deduplicates
 * by host (case-insensitive). When the same host appears in more than one
 * source, later sources win — callers should list Ingress first and HTTPRoute
 * second so Gateway API entries prefer during dual-run.
 */
public class CompositeApplicationSource implements ApplicationSource {

    private static final Logger LOG = Logger.getLogger(CompositeApplicationSource.class.getName());

    private static final Comparator<ApplicationEntry> DISPLAY_ORDER =
            Comparator.comparingInt(ApplicationEntry::getWeight)
                    .thenComparing(e -> e.getName() == null ? "" : e.getName(), String.CASE_INSENSITIVE_ORDER);

    private final List<ApplicationSource> sources;

    public CompositeApplicationSource(ApplicationSource... sources) {
        this.sources = List.of(sources);
    }

    @Override
    public List<ApplicationEntry> getApplications() {
        Map<String, ApplicationEntry> byHost = new LinkedHashMap<>();
        int rawCount = 0;
        for (ApplicationSource source : sources) {
            for (ApplicationEntry entry : source.getApplications()) {
                rawCount++;
                String host = hostKey(entry.getUrl());
                if (host == null) {
                    // Keep unparseable URLs under a unique key so they are not dropped.
                    byHost.put("url:" + entry.getUrl() + "#" + rawCount, entry);
                    continue;
                }
                ApplicationEntry previous = byHost.put(host, entry);
                if (previous != null) {
                    LOG.fine(() -> "Deduped index entry for host " + host
                            + " (kept '" + entry.getName() + "', dropped '" + previous.getName() + "')");
                }
            }
        }
        List<ApplicationEntry> merged = new ArrayList<>(byHost.values());
        merged.sort(DISPLAY_ORDER);
        return List.copyOf(merged);
    }

    public static String hostKey(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(url.trim());
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return null;
            }
            return host.toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
