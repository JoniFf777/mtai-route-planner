package com.mtai.mtairouteplanner.model.route;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record GeneratedRoutePlan(
        String templateId,
        String scene,
        String timeWindow,
        int totalBudget,
        int totalDurationMinutes,
        double totalDistanceKm,
        double routeScore,
        String startTime,
        String endTime,
        List<GeneratedRouteStop> stops,
        RouteValidationResult validationResult
) {
    public GeneratedRoutePlan {
        stops = stops == null ? List.of() : List.copyOf(stops);
    }
}


