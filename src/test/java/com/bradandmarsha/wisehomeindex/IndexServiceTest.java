package com.bradandmarsha.wisehomeindex;

import com.bradandmarsha.wisehomeindex.discovery.ApplicationSource;
import com.bradandmarsha.wisehomeindex.model.ApplicationEntry;
import com.bradandmarsha.wisehomeindex.service.IndexService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndexServiceTest {

    private IndexService service() {
        List<ApplicationEntry> apps = List.of(
                new ApplicationEntry("Plex", "https://plex.home.bradandmarsha.com", null, null, 10, true),
                new ApplicationEntry("Ceph Dashboard", "https://ceph-dashboard.home.bradandmarsha.com", null, null, 30, false),
                new ApplicationEntry("Grafana", "https://grafana-dashboard.home.bradandmarsha.com", null, null, 40, false)
        );
        ApplicationSource source = () -> apps;
        return new IndexService(source);
    }

    @Test
    void publicScopeShowsOnlyPublicApps() {
        List<ApplicationEntry> apps = service().applicationsFor(false);
        assertEquals(1, apps.size());
        assertEquals("Plex", apps.get(0).getName());
        assertTrue(apps.get(0).isPublic());
    }

    @Test
    void privateScopeShowsEverything() {
        List<ApplicationEntry> apps = service().applicationsFor(true);
        assertEquals(3, apps.size());
    }

    @Test
    void filteringPreservesSourceOrder() {
        List<ApplicationEntry> apps = service().allApplications();
        assertEquals(List.of("Plex", "Ceph Dashboard", "Grafana"),
                apps.stream().map(ApplicationEntry::getName).toList());
    }
}
