package com.mtai.mtairouteplanner.controller.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.mtai.mtairouteplanner.model.GeneratedRoutePlan;
import com.mtai.mtairouteplanner.model.PendingClarification;
import com.mtai.mtairouteplanner.model.RouteChangeRecord;
import com.mtai.mtairouteplanner.model.RouteSessionIntent;
import com.mtai.mtairouteplanner.model.RouteSessionState;
import com.mtai.mtairouteplanner.model.RouteSessionStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record RouteSessionResponse(
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
    public static RouteSessionResponse from(RouteSessionState routeSessionState) {
        return new RouteSessionResponse(
                routeSessionState.sessionId(),
                routeSessionState.userId(),
                routeSessionState.status(),
                routeSessionState.currentIntent(),
                routeSessionState.currentRoute(),
                routeSessionState.lockedStopOrders(),
                routeSessionState.pendingClarification(),
                routeSessionState.changeHistory(),
                routeSessionState.version(),
                routeSessionState.updatedAt()
        );
    }
}
