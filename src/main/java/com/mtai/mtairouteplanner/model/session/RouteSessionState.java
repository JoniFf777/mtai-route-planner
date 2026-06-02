package com.mtai.mtairouteplanner.model.session;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.mtai.mtairouteplanner.model.adjustment.RouteChangeRecord;
import com.mtai.mtairouteplanner.model.clarification.PendingClarification;
import com.mtai.mtairouteplanner.model.route.GeneratedRoutePlan;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record RouteSessionState(
        String sessionId,
        String userId,
        RouteSessionStatus status,
        RouteSessionIntent currentIntent,
        GeneratedRoutePlan currentRoute,
        Set<Integer> lockedStopOrders,
        PendingClarification pendingClarification,
        List<RouteChangeRecord> changeHistory,
        long version,
        LocalDateTime updatedAt
) {
    public RouteSessionState {
        lockedStopOrders = lockedStopOrders == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(lockedStopOrders));
        changeHistory = changeHistory == null ? List.of() : List.copyOf(changeHistory);
        status = status == null ? RouteSessionStatus.ACTIVE : status;
        updatedAt = updatedAt == null ? LocalDateTime.now() : updatedAt;
    }
}


