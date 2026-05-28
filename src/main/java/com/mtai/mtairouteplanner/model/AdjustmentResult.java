package com.mtai.mtairouteplanner.model;

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
