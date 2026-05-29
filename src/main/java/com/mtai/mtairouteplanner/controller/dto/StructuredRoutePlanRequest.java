package com.mtai.mtairouteplanner.controller.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.mtai.mtairouteplanner.model.RoutePlanRequest;

import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record StructuredRoutePlanRequest(
        String userId,
        String scene,
        String businessArea,
        String district,
        String timeWindow,
        int budgetTotal,
        int partySize,
        String pace,
        List<String> preferTags,
        List<String> avoidTags
) {
    public StructuredRoutePlanRequest {
        preferTags = preferTags == null ? List.of() : List.copyOf(preferTags);
        avoidTags = avoidTags == null ? List.of() : List.copyOf(avoidTags);
    }

    public RoutePlanRequest toRoutePlanRequest() {
        return new RoutePlanRequest(
                userId,
                scene,
                businessArea,
                district,
                timeWindow,
                budgetTotal,
                partySize,
                pace,
                preferTags,
                avoidTags
        );
    }
}
