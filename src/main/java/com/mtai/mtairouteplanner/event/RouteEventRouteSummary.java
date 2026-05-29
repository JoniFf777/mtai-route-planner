package com.mtai.mtairouteplanner.event;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record RouteEventRouteSummary(
        String templateId,
        int stopCount,
        int totalBudget,
        int totalDurationMinutes,
        double totalDistanceKm,
        List<String> stopNames
) {
    public RouteEventRouteSummary {
        stopNames = stopNames == null ? List.of() : List.copyOf(stopNames);
    }
}
