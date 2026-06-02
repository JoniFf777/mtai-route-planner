package com.mtai.mtairouteplanner.model.adjustment;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.mtai.mtairouteplanner.model.route.GeneratedRoutePlan;
import com.mtai.mtairouteplanner.model.session.RouteSessionState;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AdjustmentResult(
        String sessionId,
        AdjustmentStatus status,
        String message,
        RouteSessionState sessionState,
        GeneratedRoutePlan adjustedRoute
) {
    public boolean success() {
        return status == AdjustmentStatus.SUCCESS;
    }
}


