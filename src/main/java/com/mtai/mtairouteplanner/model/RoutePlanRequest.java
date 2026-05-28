package com.mtai.mtairouteplanner.model;

import java.util.List;

public record RoutePlanRequest(
        String userId,
        String scene,
        String businessArea,
        String district,
        String timeWindow,
        int budgetTotal,
        int partySize,
        String pace,
        List<String> preferTags,
        List<String> avoidTags
) {
    public RoutePlanRequest {
        preferTags = preferTags == null ? List.of() : List.copyOf(preferTags);
        avoidTags = avoidTags == null ? List.of() : List.copyOf(avoidTags);
        partySize = partySize <= 0 ? 1 : partySize;
    }
}
