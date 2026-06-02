package com.mtai.mtairouteplanner.controller.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.mtai.mtairouteplanner.model.route.GeneratedRoutePlan;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record StructuredRoutePlanResponse(
        String sessionId,
        String status,
        GeneratedRoutePlan route,
        String message
) {
}

