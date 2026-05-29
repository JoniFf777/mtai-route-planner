package com.mtai.mtairouteplanner.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;
import java.util.Set;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CompactRouteContext(
        String sessionId,
        String userId,
        RouteSessionIntent currentIntent,
        String currentRouteSummary,
        List<CompactRouteStop> currentRouteStops,
        Set<Integer> lockedStopOrders,
        PendingClarification pendingClarification,
        List<CompactChangeHistoryItem> latestChangeHistory,
        long version
) {
    public CompactRouteContext {
        currentRouteStops = currentRouteStops == null ? List.of() : List.copyOf(currentRouteStops);
        lockedStopOrders = lockedStopOrders == null ? Set.of() : Set.copyOf(lockedStopOrders);
        latestChangeHistory = latestChangeHistory == null ? List.of() : List.copyOf(latestChangeHistory);
    }
}
