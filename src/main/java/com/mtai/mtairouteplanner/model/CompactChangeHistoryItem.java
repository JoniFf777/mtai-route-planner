package com.mtai.mtairouteplanner.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.time.LocalDateTime;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CompactChangeHistoryItem(
        String changeId,
        String changeType,
        Integer targetStopOrder,
        String beforeRouteSummary,
        String afterRouteSummary,
        LocalDateTime createdAt
) {
    public static CompactChangeHistoryItem from(RouteChangeRecord changeRecord) {
        return new CompactChangeHistoryItem(
                changeRecord.changeId(),
                changeRecord.changeType(),
                changeRecord.targetStopOrder(),
                summarize(changeRecord.beforeRouteSnapshot()),
                summarize(changeRecord.afterRouteSnapshot()),
                changeRecord.createdAt()
        );
    }

    private static String summarize(GeneratedRoutePlan routePlan) {
        if (routePlan == null) {
            return null;
        }
        return routePlan.scene() + " / " + routePlan.stops().size() + " stops / " + routePlan.totalBudget();
    }
}
