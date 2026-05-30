package com.bradandmarsha.wisehomeindex.rest;

import com.bradandmarsha.wisehomeindex.model.ApplicationEntry;
import com.bradandmarsha.wisehomeindex.service.IndexService;
import com.bradandmarsha.wisehomeindex.util.NetworkUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;

import java.util.List;
import java.util.Map;

/**
 * JSON REST endpoint exposing the applications visible to the caller.
 *
 * <p>The visibility scope is derived from the request origin: callers on the
 * trusted subnet receive both public and private
 * applications, while all other callers receive only public applications.</p>
 */
@Path("api/applications")
public class ApplicationResource {

    private final IndexService indexService;

    public ApplicationResource(@Context IndexService indexService) {
        this.indexService = indexService;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> list(@Context HttpServletRequest request) {
        boolean privateRequest = NetworkUtil.isPrivateRequest(request);
        List<ApplicationEntry> applications = indexService.applicationsFor(privateRequest);

        return Map.of(
                "scope", privateRequest ? "private" : "public",
                "clientIp", String.valueOf(NetworkUtil.resolveClientIp(request)),
                "trustedPublicIps", NetworkUtil.resolvedPublicIps(),
                "count", applications.size(),
                "applications", applications
        );
    }
}
