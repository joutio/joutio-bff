package com.joutio.bff.application;

import java.util.List;

/**
 * Page de listing du stockage : {@code nextContinuationToken} non nul tant qu'il reste des objets.
 */
public record MediaObjectPage(List<MediaObject> objects, String nextContinuationToken) {

    public boolean hasMore() {
        return nextContinuationToken != null;
    }
}
