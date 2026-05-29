package com.mtai.mtairouteplanner.controller.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.mtai.mtairouteplanner.model.GeneratedRoutePlan;
import com.mtai.mtairouteplanner.model.RouteSessionState;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record NaturalLanguageRouteResponse(
        String sessionId,
        String status,
        GeneratedRoutePlan route,
        String message,
        RouteSessionResponse session
) {
    public static NaturalLanguageRouteResponse success(
            String sessionId,
            String status,
            GeneratedRoutePlan route,
            String message,
            RouteSessionState routeSessionState
    ) {
        return new NaturalLanguageRouteResponse(
                sessionId,
                status,
                route,
                message,
                routeSessionState == null ? null : RouteSessionResponse.from(routeSessionState)
        );
    }

    public static NaturalLanguageRouteResponse failure(String status, String message) {
        return new NaturalLanguageRouteResponse(null, status, null, message, null);
    }
}
