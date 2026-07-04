package com.bradandmarsha.wisehomeindex;

import com.bradandmarsha.wisehomeindex.discovery.DiscoverySettings;
import com.bradandmarsha.wisehomeindex.discovery.IngressMapper;
import com.bradandmarsha.wisehomeindex.model.ApplicationEntry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IngressMapperTest {

    private static final String PREFIX = "index.home.bradandmarsha.com";

    private final IngressMapper mapper = new IngressMapper(
            new DiscoverySettings(PREFIX, "nginx", "nginx-internal", Duration.ofSeconds(30)));

    private static Map<String, String> annotations(String... kv) {
        Map<String, String> map = new java.util.HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put(kv[i], kv[i + 1]);
        }
        return map;
    }

    private IngressMapper.IngressView view(String ingressClass, Map<String, String> annotations,
                                           List<String> ruleHosts, List<String> tlsHosts) {
        return new IngressMapper.IngressView("ingress", "default", ingressClass, annotations, ruleHosts, tlsHosts);
    }

    @Test
    void mapsEnabledPublicIngress() {
        Optional<ApplicationEntry> result = mapper.map(view("nginx",
                annotations(
                        PREFIX + "/enabled", "true",
                        PREFIX + "/name", "Flask Hello World",
                        PREFIX + "/image", "https://media.home.bradandmarsha.com/media/flaskred.png",
                        PREFIX + "/description", "Sample Flask app",
                        PREFIX + "/weight", "20"),
                List.of("flask-hello-world.home.bradandmarsha.com"),
                List.of("flask-hello-world.home.bradandmarsha.com")));

        assertTrue(result.isPresent());
        ApplicationEntry app = result.get();
        assertEquals("Flask Hello World", app.getName());
        assertEquals("https://flask-hello-world.home.bradandmarsha.com", app.getUrl());
        assertEquals("https://media.home.bradandmarsha.com/media/flaskred.png", app.getImage());
        assertEquals("Sample Flask app", app.getDescription());
        assertEquals(20, app.getWeight());
        assertTrue(app.isPublic());
    }

    @Test
    void internalClassIsPrivateEvenForPublicSubdomainHost() {
        Optional<ApplicationEntry> result = mapper.map(view("nginx-internal",
                annotations(
                        PREFIX + "/enabled", "true",
                        PREFIX + "/name", "Ceph Dashboard"),
                List.of("ceph-dashboard.home.bradandmarsha.com"),
                List.of("ceph-dashboard.home.bradandmarsha.com")));

        assertTrue(result.isPresent());
        assertFalse(result.get().isPublic());
    }

    @Test
    void skipsWhenNotEnabled() {
        assertTrue(mapper.map(view("nginx",
                annotations(PREFIX + "/name", "Nope"),
                List.of("nope.home.bradandmarsha.com"), List.of())).isEmpty());

        assertTrue(mapper.map(view("nginx",
                annotations(PREFIX + "/enabled", "false", PREFIX + "/name", "Nope"),
                List.of("nope.home.bradandmarsha.com"), List.of())).isEmpty());
    }

    @Test
    void skipsWhenNameMissing() {
        assertTrue(mapper.map(view("nginx",
                annotations(PREFIX + "/enabled", "true"),
                List.of("x.home.bradandmarsha.com"), List.of())).isEmpty());
    }

    @Test
    void skipsWhenNoHost() {
        assertTrue(mapper.map(view("nginx",
                annotations(PREFIX + "/enabled", "true", PREFIX + "/name", "No Host"),
                List.of(), List.of())).isEmpty());
    }

    @Test
    void fallsBackToTlsHostAndDefaultsWeight() {
        Optional<ApplicationEntry> result = mapper.map(view("nginx",
                annotations(PREFIX + "/enabled", "true", PREFIX + "/name", "Tls Only"),
                List.of(),
                List.of("tls-only.home.bradandmarsha.com")));

        assertTrue(result.isPresent());
        ApplicationEntry app = result.get();
        assertEquals("https://tls-only.home.bradandmarsha.com", app.getUrl());
        assertEquals(IngressMapper.DEFAULT_WEIGHT, app.getWeight());
        assertNull(app.getImage());
        assertNull(app.getDescription());
    }

    @Test
    void usesHttpWhenNoTls() {
        Optional<ApplicationEntry> result = mapper.map(view("nginx",
                annotations(PREFIX + "/enabled", "true", PREFIX + "/name", "Plain"),
                List.of("plain.home.bradandmarsha.com"),
                List.of()));

        assertTrue(result.isPresent());
        assertEquals("http://plain.home.bradandmarsha.com", result.get().getUrl());
    }

    @Test
    void invalidWeightUsesDefault() {
        Optional<ApplicationEntry> result = mapper.map(view("nginx",
                annotations(PREFIX + "/enabled", "true", PREFIX + "/name", "Bad Weight",
                        PREFIX + "/weight", "heavy"),
                List.of("bad.home.bradandmarsha.com"), List.of()));

        assertTrue(result.isPresent());
        assertEquals(IngressMapper.DEFAULT_WEIGHT, result.get().getWeight());
    }

    @Test
    void unknownClassDefaultsToPrivate() {
        Optional<ApplicationEntry> result = mapper.map(view("traefik",
                annotations(PREFIX + "/enabled", "true", PREFIX + "/name", "Mystery"),
                List.of("mystery.home.bradandmarsha.com"), List.of()));

        assertTrue(result.isPresent());
        assertFalse(result.get().isPublic());
    }
}
