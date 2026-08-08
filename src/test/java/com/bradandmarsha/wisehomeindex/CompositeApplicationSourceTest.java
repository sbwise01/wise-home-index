package com.bradandmarsha.wisehomeindex;

import com.bradandmarsha.wisehomeindex.discovery.ApplicationSource;
import com.bradandmarsha.wisehomeindex.discovery.CompositeApplicationSource;
import com.bradandmarsha.wisehomeindex.model.ApplicationEntry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CompositeApplicationSourceTest {

    @Test
    void prefersLaterSourceOnSameHost() {
        ApplicationSource ingress = () -> List.of(
                new ApplicationEntry("Flask Ingress", "https://flask.home.bradandmarsha.com", null, null, 20, true),
                new ApplicationEntry("Plex", "https://plex.home.bradandmarsha.com", null, null, 10, true));
        ApplicationSource routes = () -> List.of(
                new ApplicationEntry("Flask Route", "https://flask.home.bradandmarsha.com", null, null, 20, true));

        List<ApplicationEntry> apps = new CompositeApplicationSource(ingress, routes).getApplications();

        assertEquals(2, apps.size());
        assertEquals(List.of("Plex", "Flask Route"),
                apps.stream().map(ApplicationEntry::getName).toList());
    }

    @Test
    void hostKeyIsCaseInsensitive() {
        assertEquals("flask.home.bradandmarsha.com",
                CompositeApplicationSource.hostKey("https://Flask.Home.Bradandmarsha.com/"));
    }
}
