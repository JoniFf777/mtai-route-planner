package com.mtai.mtairouteplanner.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

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
