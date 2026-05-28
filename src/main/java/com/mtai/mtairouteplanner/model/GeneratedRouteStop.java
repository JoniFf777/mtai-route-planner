package com.mtai.mtairouteplanner.model;

import java.util.List;

public record GeneratedRouteStop(
        int stopOrder,
        String slotRole,
        String poiId,
        String poiName,
        String businessArea,
        String district,
        String categoryLv1,
        String indoorOutdoor,
        String arriveTime,
        String leaveTime,
        int stayMinutes,
        double travelMinutesFromPrev,
        double distanceKmFromPrev,
        int estimatedCost,
        double stopScore,
        List<String> matchedPreferTags,
        List<String> matchedAvoidTags
) {
    public GeneratedRouteStop {
        matchedPreferTags = matchedPreferTags == null ? List.of() : List.copyOf(matchedPreferTags);
        matchedAvoidTags = matchedAvoidTags == null ? List.of() : List.copyOf(matchedAvoidTags);
    }
}
