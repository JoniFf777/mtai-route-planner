package com.mtai.mtairouteplanner.service;

import com.mtai.mtairouteplanner.model.CompactChangeHistoryItem;
import com.mtai.mtairouteplanner.model.CompactRouteContext;
import com.mtai.mtairouteplanner.model.CompactRouteStop;
import com.mtai.mtairouteplanner.model.GeneratedRoutePlan;
import com.mtai.mtairouteplanner.model.RouteSessionState;

import java.util.List;

public class RouteContextAssembler {

    private static final int MAX_HISTORY_ITEMS = 5;

    public CompactRouteContext assemble(RouteSessionState routeSessionState) {
        List<CompactRouteStop> compactRouteStops = routeSessionState.currentRoute() == null
                ? List.of()
                : routeSessionState.currentRoute().stops().stream()
                .map(CompactRouteStop::from)
                .toList();

        List<CompactChangeHistoryItem> latestChangeHistory = routeSessionState.changeHistory().stream()
                .skip(Math.max(0, routeSessionState.changeHistory().size() - MAX_HISTORY_ITEMS))
                .map(CompactChangeHistoryItem::from)
                .toList();

        return new CompactRouteContext(
                routeSessionState.sessionId(),
                routeSessionState.userId(),
                routeSessionState.currentIntent(),
                summarizeRoute(routeSessionState.currentRoute()),
                compactRouteStops,
                routeSessionState.lockedStopOrders(),
                routeSessionState.pendingClarification(),
                latestChangeHistory,
                routeSessionState.version()
        );
    }

    private String summarizeRoute(GeneratedRoutePlan routePlan) {
        if (routePlan == null) {
            return null;
        }
        return routePlan.scene()
                + " | " + routePlan.stops().size() + " stops"
                + " | budget " + routePlan.totalBudget()
                + " | " + routePlan.startTime() + "-" + routePlan.endTime();
    }
}
