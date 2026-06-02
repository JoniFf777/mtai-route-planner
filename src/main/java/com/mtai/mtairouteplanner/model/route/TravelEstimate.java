package com.mtai.mtairouteplanner.model.route;

public record TravelEstimate(
        String fromPoiId,
        String toPoiId,
        String fromBusinessArea,
        String toBusinessArea,
        double distanceKm,
        double estimatedMinutes,
        String transportMode,
        String estimateSource
) {
}


