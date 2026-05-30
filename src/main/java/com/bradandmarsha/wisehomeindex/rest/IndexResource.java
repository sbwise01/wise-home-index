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

/**
 * Serves the HTML index page. The set of tiles rendered depends on the request
 * origin (see {@link NetworkUtil}).
 */
@Path("/")
public class IndexResource {

    private final IndexService indexService;

    public IndexResource(@Context IndexService indexService) {
        this.indexService = indexService;
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    public String index(@Context HttpServletRequest request) {
        boolean privateRequest = NetworkUtil.isPrivateRequest(request);
        List<ApplicationEntry> applications = indexService.applicationsFor(privateRequest);
        return HtmlRenderer.render(applications, privateRequest);
    }
}
