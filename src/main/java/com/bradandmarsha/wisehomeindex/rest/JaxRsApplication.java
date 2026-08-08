package com.bradandmarsha.wisehomeindex.rest;

import com.bradandmarsha.wisehomeindex.discovery.ApplicationSource;
import com.bradandmarsha.wisehomeindex.discovery.CompositeApplicationSource;
import com.bradandmarsha.wisehomeindex.discovery.DiscoverySettings;
import com.bradandmarsha.wisehomeindex.discovery.HttpRouteApplicationSource;
import com.bradandmarsha.wisehomeindex.discovery.IngressApplicationSource;
import com.bradandmarsha.wisehomeindex.service.IndexService;
import jakarta.ws.rs.ApplicationPath;
import org.glassfish.jersey.internal.inject.AbstractBinder;
import org.glassfish.jersey.server.ResourceConfig;

/**
 * JAX-RS application wiring. Registers the REST resources and exposes a single
 * shared {@link IndexService} instance backed by Kubernetes Ingress and
 * HTTPRoute discovery (HTTPRoute wins on host collision during dual-run).
 *
 * <p>Mounted as a servlet filter (see {@code web.xml}) so that unmatched
 * requests fall through to Tomcat's default servlet for static assets.</p>
 */
@ApplicationPath("/")
public class JaxRsApplication extends ResourceConfig {

    public JaxRsApplication() {
        final DiscoverySettings settings = DiscoverySettings.fromEnvironment();
        final ApplicationSource source = new CompositeApplicationSource(
                new IngressApplicationSource(settings),
                new HttpRouteApplicationSource(settings));
        final IndexService indexService = new IndexService(source);

        register(new AbstractBinder() {
            @Override
            protected void configure() {
                bind(indexService).to(IndexService.class);
            }
        });

        packages("com.bradandmarsha.wisehomeindex.rest");
    }
}
