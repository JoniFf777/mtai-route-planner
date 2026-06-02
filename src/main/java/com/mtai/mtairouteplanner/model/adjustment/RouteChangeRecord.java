package com.mtai.mtairouteplanner.model.adjustment;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.mtai.mtairouteplanner.model.route.GeneratedRoutePlan;

import java.time.LocalDateTime;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record RouteChangeRecord(
        String changeId,
        String changeType,
        String rawChangeQuery,
        Integer targetStopOrder,
        GeneratedRoutePlan beforeRouteSnapshot,
        GeneratedRoutePlan afterRouteSnapshot,
        LocalDateTime createdAt
) {
    public RouteChangeRecord {
        createdAt = createdAt == null ? LocalDateTime.now() : createdAt;
    }
}


