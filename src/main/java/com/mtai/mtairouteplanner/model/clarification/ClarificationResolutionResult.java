package com.mtai.mtairouteplanner.model.clarification;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.mtai.mtairouteplanner.model.adjustment.ChangeRequest;
import com.mtai.mtairouteplanner.model.route.GeneratedRoutePlan;
import com.mtai.mtairouteplanner.model.session.RouteSessionState;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ClarificationResolutionResult(
        String sessionId,
        String status,
        String message,
        ChangeRequest resolvedChangeRequest,
        RouteSessionState sessionState,
        GeneratedRoutePlan adjustedRoute
) {
}


