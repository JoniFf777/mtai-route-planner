package com.mtai.mtairouteplanner.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record GeneratedRouteStop(
        int stopOrder,
        String slotRole,
        String poiId,
        String poiName,
        String businessArea,
        String district,
        double lng,
        double lat,
        String coordinateSystem,
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
