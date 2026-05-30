package com.bradandmarsha.wisehomeindex.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Root object for the YAML configuration file.
 *
 * <pre>
 * applications:
 *   - name: Grafana
 *     url: https://grafana.home.bradandmarsha.com
 *     image: https://grafana.home.bradandmarsha.com/favicon.ico
 *   - name: Ceph Dashboard
 *     url: ceph-dashboard
 * </pre>
 */
public class IndexConfig {

    private List<ApplicationEntry> applications = new ArrayList<>();

    public List<ApplicationEntry> getApplications() {
        return applications;
    }

    public void setApplications(List<ApplicationEntry> applications) {
        this.applications = applications != null ? applications : new ArrayList<>();
    }
}
