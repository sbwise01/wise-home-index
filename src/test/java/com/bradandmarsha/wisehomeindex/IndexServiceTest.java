package com.bradandmarsha.wisehomeindex;

import com.bradandmarsha.wisehomeindex.model.ApplicationEntry;
import com.bradandmarsha.wisehomeindex.model.IndexConfig;
import com.bradandmarsha.wisehomeindex.service.IndexService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndexServiceTest {

    private IndexService service() {
        IndexConfig config = new IndexConfig();
        config.setApplications(List.of(
                new ApplicationEntry("Grafana", "https://grafana.home.bradandmarsha.com", null),
                new ApplicationEntry("Ceph Dashboard", "ceph-dashboard", null),
                new ApplicationEntry("Plex k8s", "wise-plex-k8s", null)
        ));
        return new IndexService(config);
    }

    @Test
    void publicScopeShowsOnlyPublicUrls() {
        List<ApplicationEntry> apps = service().applicationsFor(false);
        assertEquals(1, apps.size());
        assertEquals("Grafana", apps.get(0).getName());
        assertTrue(apps.get(0).isPublic());
    }

    @Test
    void privateScopeShowsEverything() {
        List<ApplicationEntry> apps = service().applicationsFor(true);
        assertEquals(3, apps.size());
    }

    @Test
    void publicUrlClassification() {
        assertTrue(new ApplicationEntry("a", "https://x.home.bradandmarsha.com", null).isPublic());
        assertTrue(new ApplicationEntry("a", "x.home.bradandmarsha.com", null).isPublic());
        assertTrue(!new ApplicationEntry("a", "ceph-dashboard", null).isPublic());
        assertTrue(!new ApplicationEntry("a", "https://example.com", null).isPublic());
    }
}
