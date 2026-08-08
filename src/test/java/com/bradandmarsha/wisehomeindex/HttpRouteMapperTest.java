package com.bradandmarsha.wisehomeindex;

import com.bradandmarsha.wisehomeindex.discovery.DiscoverySettings;
import com.bradandmarsha.wisehomeindex.discovery.HttpRouteMapper;
import com.bradandmarsha.wisehomeindex.model.ApplicationEntry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpRouteMapperTest {

    private static final String PREFIX = "index.home.bradandmarsha.com";

    private final HttpRouteMapper mapper = new HttpRouteMapper(
            new DiscoverySettings(PREFIX, "nginx", "nginx-internal",
                    "gateway-public", "gateway-internal", Duration.ofSeconds(30)));

    private static Map<String, String> annotations(String... kv) {
        Map<String, String> map = new java.util.HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put(kv[i], kv[i + 1]);
        }
        return map;
    }

    private HttpRouteMapper.HttpRouteView view(Map<String, String> annotations,
                                               List<String> hostnames,
                                               List<String> gateways,
                                               List<String> sections) {
        return new HttpRouteMapper.HttpRouteView(
                "route", "default", annotations, hostnames, gateways, sections);
    }

    @Test
    void mapsEnabledPublicHttpRoute() {
        Optional<ApplicationEntry> result = mapper.map(view(
                annotations(
                        PREFIX + "/enabled", "true",
                        PREFIX + "/name", "Flask Hello World",
                        PREFIX + "/weight", "20"),
                List.of("flask-hello-world.home.bradandmarsha.com"),
                List.of("gateway-public"),
                List.of("https-flask-hello-world")));

        assertTrue(result.isPresent());
        ApplicationEntry app = result.get();
        assertEquals("Flask Hello World", app.getName());
        assertEquals("https://flask-hello-world.home.bradandmarsha.com", app.getUrl());
        assertEquals(20, app.getWeight());
        assertTrue(app.isPublic());
    }

    @Test
    void internalGatewayIsPrivate() {
        Optional<ApplicationEntry> result = mapper.map(view(
                annotations(PREFIX + "/enabled", "true", PREFIX + "/name", "Grafana"),
                List.of("grafana-dashboard.home.bradandmarsha.com"),
                List.of("gateway-internal"),
                List.of("https")));

        assertTrue(result.isPresent());
        assertFalse(result.get().isPublic());
    }

    @Test
    void skipsWhenNotEnabled() {
        assertTrue(mapper.map(view(
                annotations(PREFIX + "/name", "Nope"),
                List.of("nope.home.bradandmarsha.com"),
                List.of("gateway-public"),
                List.of("https"))).isEmpty());
    }

    @Test
    void skipsRedirectOnlyRoutesWithoutIndexAnnotations() {
        assertTrue(mapper.map(view(
                Map.of(),
                List.of("flask-hello-world.home.bradandmarsha.com"),
                List.of("gateway-public"),
                List.of("http"))).isEmpty());
    }

    @Test
    void httpOnlySectionUsesHttpScheme() {
        Optional<ApplicationEntry> result = mapper.map(view(
                annotations(PREFIX + "/enabled", "true", PREFIX + "/name", "Plain"),
                List.of("plain.home.bradandmarsha.com"),
                List.of("gateway-public"),
                List.of("http")));

        assertTrue(result.isPresent());
        assertEquals("http://plain.home.bradandmarsha.com", result.get().getUrl());
    }

    @Test
    void unknownParentGatewayDefaultsToPrivate() {
        Optional<ApplicationEntry> result = mapper.map(view(
                annotations(PREFIX + "/enabled", "true", PREFIX + "/name", "Mystery"),
                List.of("mystery.home.bradandmarsha.com"),
                List.of("some-other-gateway"),
                List.of("https")));

        assertTrue(result.isPresent());
        assertFalse(result.get().isPublic());
    }
}
