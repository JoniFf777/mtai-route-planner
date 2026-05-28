package com.mtai.mtairouteplanner.model;

import java.util.List;

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
