package com.bradandmarsha.wisehomeindex.discovery;

import com.bradandmarsha.wisehomeindex.model.ApplicationEntry;

import java.util.List;

/**
 * Supplies the current set of applications to display, ordered for presentation
 * (ascending by weight, then name). Implementations decide where the data comes
 * from and how (or whether) it is cached.
 */
public interface ApplicationSource {

    /**
     * @return the current, presentation-ordered list of applications
     */
    List<ApplicationEntry> getApplications();
}
