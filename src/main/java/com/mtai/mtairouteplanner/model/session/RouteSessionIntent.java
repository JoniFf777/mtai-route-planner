package com.mtai.mtairouteplanner.model.session;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.mtai.mtairouteplanner.model.route.RoutePlanRequest;

import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record RouteSessionIntent(
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
    public RouteSessionIntent {
        preferTags = preferTags == null ? List.of() : List.copyOf(preferTags);
        avoidTags = avoidTags == null ? List.of() : List.copyOf(avoidTags);
        partySize = partySize <= 0 ? 1 : partySize;
    }

    public static RouteSessionIntent from(RoutePlanRequest routePlanRequest) {
        if (routePlanRequest == null) {
            return null;
        }
        return new RouteSessionIntent(
                routePlanRequest.scene(),
                routePlanRequest.businessArea(),
                routePlanRequest.district(),
                routePlanRequest.timeWindow(),
                routePlanRequest.budgetTotal(),
                routePlanRequest.partySize(),
                routePlanRequest.pace(),
                routePlanRequest.preferTags(),
                routePlanRequest.avoidTags()
        );
    }

    public RoutePlanRequest toRoutePlanRequest(String userId) {
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


