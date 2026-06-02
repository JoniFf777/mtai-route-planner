package com.mtai.mtairouteplanner.controller.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record NaturalLanguageRouteRequest(
        String userId,
        String message
) {
}

