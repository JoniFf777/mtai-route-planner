package com.mtai.mtairouteplanner.model;

import java.time.LocalDateTime;

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
