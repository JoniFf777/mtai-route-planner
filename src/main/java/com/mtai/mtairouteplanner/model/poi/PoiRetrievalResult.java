package com.mtai.mtairouteplanner.model.poi;

import java.util.List;

public record PoiRetrievalResult(
        PoiSearchRequest request,
        int totalMatchedCount,
        List<PoiCandidate> candidates
) {
    public PoiRetrievalResult {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }
}


