package com.bradandmarsha.wisehomeindex;

import com.bradandmarsha.wisehomeindex.discovery.HttpRouteApplicationSource;
import com.bradandmarsha.wisehomeindex.discovery.HttpRouteMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpRouteApplicationSourceTest {

    @Test
    void parsesCustomObjectListIntoViews() {
        Map<String, Object> response = Map.of(
                "items", List.of(
                        Map.of(
                                "metadata", Map.of(
                                        "name", "flask-hello-world",
                                        "namespace", "default",
                                        "annotations", Map.of(
                                                "index.home.bradandmarsha.com/enabled", "true",
                                                "index.home.bradandmarsha.com/name", "Flask")),
                                "spec", Map.of(
                                        "hostnames", List.of("flask-hello-world.home.bradandmarsha.com"),
                                        "parentRefs", List.of(
                                                Map.of(
                                                        "name", "gateway-public",
                                                        "namespace", "kgateway-system",
                                                        "sectionName", "https-flask-hello-world"))))));

        List<HttpRouteMapper.HttpRouteView> views = HttpRouteApplicationSource.toViews(response);
        assertEquals(1, views.size());
        HttpRouteMapper.HttpRouteView view = views.get(0);
        assertEquals("flask-hello-world", view.name());
        assertEquals("default", view.namespace());
        assertEquals(List.of("flask-hello-world.home.bradandmarsha.com"), view.hostnames());
        assertEquals(List.of("gateway-public"), view.parentGatewayNames());
        assertEquals(List.of("https-flask-hello-world"), view.parentSectionNames());
        assertTrue(Boolean.parseBoolean(view.annotations().get("index.home.bradandmarsha.com/enabled")));
    }
}
