package com.mtai.mtairouteplanner.event.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.time.LocalDateTime;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record RouteLifecycleEvent(
        String eventId,
        RouteEventType eventType,
        String sessionId,
        String userId,
        String routeScene,
        String routeStatus,
        String changeType,
        RouteEventRouteSummary routeSummary,
        String issueReason,
        LocalDateTime createdAt
) {
}


