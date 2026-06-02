package com.mtai.mtairouteplanner.event.service;

import com.mtai.mtairouteplanner.event.model.RouteEventRouteSummary;
import com.mtai.mtairouteplanner.event.model.RouteEventType;
import com.mtai.mtairouteplanner.event.model.RouteLifecycleEvent;
import com.mtai.mtairouteplanner.event.publisher.RouteEventPublisher;
import com.mtai.mtairouteplanner.model.adjustment.AdjustmentResult;
import com.mtai.mtairouteplanner.model.adjustment.ChangeType;
import com.mtai.mtairouteplanner.model.clarification.ClarificationResolutionResult;
import com.mtai.mtairouteplanner.model.route.GeneratedRoutePlan;
import com.mtai.mtairouteplanner.model.route.GeneratedRouteStop;
import com.mtai.mtairouteplanner.model.route.RoutePlanRequest;
import com.mtai.mtairouteplanner.model.session.RouteSessionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class RouteLifecycleEventService {

    private static final Logger log = LoggerFactory.getLogger(RouteLifecycleEventService.class);

    private final RouteEventPublisher routeEventPublisher;

    public RouteLifecycleEventService(RouteEventPublisher routeEventPublisher) {
        this.routeEventPublisher = routeEventPublisher;
    }

    public void publishRoutePlanned(RouteSessionState sessionState) {
        if (sessionState == null) {
            return;
        }
        publish(new RouteLifecycleEvent(
                nextEventId(),
                RouteEventType.ROUTE_PLANNED,
                sessionState.sessionId(),
                sessionState.userId(),
                sceneOf(sessionState.currentRoute(), sessionState),
                "SUCCESS",
                null,
                summarize(sessionState.currentRoute()),
                null,
                LocalDateTime.now()
        ));
    }

    public void publishRoutePlanFailed(String userId, RoutePlanRequest routePlanRequest, String issueReason) {
        publish(new RouteLifecycleEvent(
                nextEventId(),
                RouteEventType.ROUTE_PLAN_FAILED,
                null,
                userId,
                routePlanRequest == null ? null : routePlanRequest.scene(),
                "FAILED",
                null,
                null,
                issueReason,
                LocalDateTime.now()
        ));
    }

    public void publishRouteAdjusted(AdjustmentResult adjustmentResult, ChangeType changeType) {
        publishAdjustmentEvent(RouteEventType.ROUTE_ADJUSTED, adjustmentResult, changeType);
    }

    public void publishRouteAdjustmentFailed(AdjustmentResult adjustmentResult, ChangeType changeType) {
        publishAdjustmentEvent(RouteEventType.ROUTE_ADJUSTMENT_FAILED, adjustmentResult, changeType);
    }

    public void publishRouteWaitingClarification(AdjustmentResult adjustmentResult, ChangeType changeType) {
        publishAdjustmentEvent(RouteEventType.ROUTE_WAITING_CLARIFICATION, adjustmentResult, changeType);
    }

    public void publishClarificationResolved(ClarificationResolutionResult resolutionResult) {
        if (resolutionResult == null || resolutionResult.sessionState() == null) {
            return;
        }
        RouteSessionState sessionState = resolutionResult.sessionState();
        publish(new RouteLifecycleEvent(
                nextEventId(),
                RouteEventType.ROUTE_CLARIFICATION_RESOLVED,
                resolutionResult.sessionId(),
                sessionState.userId(),
                sceneOf(sessionState.currentRoute(), sessionState),
                resolutionResult.status(),
                resolutionResult.resolvedChangeRequest() == null || resolutionResult.resolvedChangeRequest().changeType() == null
                        ? null
                        : resolutionResult.resolvedChangeRequest().changeType().name(),
                summarize(sessionState.currentRoute()),
                resolutionResult.message(),
                LocalDateTime.now()
        ));
    }

    private void publishAdjustmentEvent(RouteEventType eventType, AdjustmentResult adjustmentResult, ChangeType changeType) {
        if (adjustmentResult == null) {
            return;
        }
        RouteSessionState sessionState = adjustmentResult.sessionState();
        publish(new RouteLifecycleEvent(
                nextEventId(),
                eventType,
                adjustmentResult.sessionId(),
                sessionState == null ? null : sessionState.userId(),
                sceneOf(adjustmentResult.adjustedRoute(), sessionState),
                adjustmentResult.status() == null ? null : adjustmentResult.status().name(),
                changeType == null ? null : changeType.name(),
                summarize(adjustmentResult.adjustedRoute() != null
                        ? adjustmentResult.adjustedRoute()
                        : (sessionState == null ? null : sessionState.currentRoute())),
                adjustmentResult.message(),
                LocalDateTime.now()
        ));
    }

    private void publish(RouteLifecycleEvent event) {
        try {
            routeEventPublisher.publish(event);
        } catch (Exception exception) {
            log.warn("Route event publish failed safely. eventType={} sessionId={}",
                    event.eventType(), event.sessionId(), exception);
        }
    }

    private RouteEventRouteSummary summarize(GeneratedRoutePlan routePlan) {
        if (routePlan == null) {
            return null;
        }
        List<String> stopNames = routePlan.stops().stream()
                .map(GeneratedRouteStop::poiName)
                .filter(name -> name != null && !name.isBlank())
                .toList();
        return new RouteEventRouteSummary(
                routePlan.templateId(),
                routePlan.stops().size(),
                routePlan.totalBudget(),
                routePlan.totalDurationMinutes(),
                routePlan.totalDistanceKm(),
                stopNames
        );
    }

    private String sceneOf(GeneratedRoutePlan routePlan, RouteSessionState sessionState) {
        if (routePlan != null && routePlan.scene() != null && !routePlan.scene().isBlank()) {
            return routePlan.scene();
        }
        return sessionState == null || sessionState.currentIntent() == null ? null : sessionState.currentIntent().scene();
    }

    private String nextEventId() {
        return "EVT-" + UUID.randomUUID();
    }
}


