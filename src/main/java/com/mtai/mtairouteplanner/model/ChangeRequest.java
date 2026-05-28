package com.mtai.mtairouteplanner.model;

import java.util.List;

public record ChangeRequest(
        ChangeType changeType,
        Integer targetStopOrder,
        String targetSlotRole,
        Integer newBudgetTotal,
        String newTimeWindow,
        List<String> preferTags,
        List<String> avoidTags,
        List<Integer> lockedStopOrders
) {
    public ChangeRequest {
        preferTags = preferTags == null ? List.of() : List.copyOf(preferTags);
        avoidTags = avoidTags == null ? List.of() : List.copyOf(avoidTags);
        lockedStopOrders = lockedStopOrders == null ? List.of() : List.copyOf(lockedStopOrders);
    }
}
