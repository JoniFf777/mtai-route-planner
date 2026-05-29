package com.mtai.mtairouteplanner.ai;

import com.mtai.mtairouteplanner.model.AdjustmentResult;
import com.mtai.mtairouteplanner.model.GeneratedRoutePlan;
import com.mtai.mtairouteplanner.model.GeneratedRouteStop;
import com.mtai.mtairouteplanner.model.PendingClarification;
import com.mtai.mtairouteplanner.model.RouteChangeRecord;
import com.mtai.mtairouteplanner.model.RoutePlanRequest;
import com.mtai.mtairouteplanner.model.RouteSessionState;
import com.mtai.mtairouteplanner.model.RouteValidationIssue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class SpringAiPresenterAgentService implements PresenterAgentService {

    private static final Logger log = LoggerFactory.getLogger(SpringAiPresenterAgentService.class);

    private final PresenterGenerationGateway presenterGenerationGateway;
    private final PresenterAgentService fallbackPresenterAgentService;
    private final String presenterSystemPrompt;

    public SpringAiPresenterAgentService(
            PresenterGenerationGateway presenterGenerationGateway,
            PresenterAgentService fallbackPresenterAgentService,
            String presenterSystemPrompt
    ) {
        this.presenterGenerationGateway = Objects.requireNonNull(presenterGenerationGateway, "presenterGenerationGateway must not be null");
        this.fallbackPresenterAgentService = Objects.requireNonNull(fallbackPresenterAgentService, "fallbackPresenterAgentService must not be null");
        this.presenterSystemPrompt = Objects.requireNonNull(presenterSystemPrompt, "presenterSystemPrompt must not be null");
    }

    @Override
    public String presentInitialRoute(RouteSessionState routeSessionState) {
        if (routeSessionState == null || routeSessionState.currentRoute() == null) {
            return fallbackPresenterAgentService.presentInitialRoute(routeSessionState);
        }
        return generateOrFallback(
                buildInitialRoutePrompt(routeSessionState),
                routeSessionState.lockedStopOrders(),
                () -> fallbackPresenterAgentService.presentInitialRoute(routeSessionState)
        );
    }

    @Override
    public String presentAdjustmentResult(AdjustmentResult adjustmentResult) {
        if (adjustmentResult == null || adjustmentResult.sessionState() == null) {
            return fallbackPresenterAgentService.presentAdjustmentResult(adjustmentResult);
        }
        if (adjustmentResult.sessionState().pendingClarification() != null && adjustmentResult.adjustedRoute() == null) {
            return presentClarification(adjustmentResult.sessionState());
        }
        return generateOrFallback(
                buildAdjustmentPrompt(adjustmentResult),
                adjustmentResult.sessionState().lockedStopOrders(),
                () -> fallbackPresenterAgentService.presentAdjustmentResult(adjustmentResult)
        );
    }

    @Override
    public String presentClarification(RouteSessionState routeSessionState) {
        if (routeSessionState == null || routeSessionState.pendingClarification() == null) {
            return fallbackPresenterAgentService.presentClarification(routeSessionState);
        }
        return generateOrFallback(
                buildClarificationPrompt(routeSessionState),
                routeSessionState.lockedStopOrders(),
                () -> fallbackPresenterAgentService.presentClarification(routeSessionState)
        );
    }

    @Override
    public String presentNoFeasibleRoute(RoutePlanRequest routePlanRequest) {
        if (routePlanRequest == null) {
            return fallbackPresenterAgentService.presentNoFeasibleRoute(routePlanRequest);
        }
        return generateOrFallback(
                buildNoFeasibleRoutePrompt(routePlanRequest),
                null,
                () -> fallbackPresenterAgentService.presentNoFeasibleRoute(routePlanRequest)
        );
    }

    private String generateOrFallback(String userPrompt, Set<Integer> lockedStopOrders, FallbackSupplier fallbackSupplier) {
        try {
            String generated = presenterGenerationGateway.generate(presenterSystemPrompt, userPrompt);
            if (hasText(generated)) {
                String normalized = generated.trim();
                if (mentionsLockedStopsWithoutInput(normalized, lockedStopOrders)) {
                    log.warn("Spring AI presenter mentioned locked stops even though locked_stop_orders is empty. Falling back to FakePresenterService.");
                } else {
                    return normalized;
                }
            }
            else {
                log.warn("Spring AI presenter returned blank output. Falling back to FakePresenterService.");
            }
        } catch (RuntimeException exception) {
            log.warn("Spring AI presenter failed. Falling back to FakePresenterService.", exception);
        }
        return fallbackSupplier.get();
    }

    private boolean mentionsLockedStopsWithoutInput(String output, Set<Integer> lockedStopOrders) {
        if (lockedStopOrders != null && !lockedStopOrders.isEmpty()) {
            return false;
        }
        return output.contains("锁定") || output.contains("锁住") || output.contains("已锁");
    }

    private String buildInitialRoutePrompt(RouteSessionState routeSessionState) {
        return """
                presentation_type: INITIAL_ROUTE
                locked_stop_orders: %s
                route:
                %s
                """.formatted(
                sortedLockedStops(routeSessionState.lockedStopOrders()),
                summarizeRoute(routeSessionState.currentRoute())
        );
    }

    private String buildAdjustmentPrompt(AdjustmentResult adjustmentResult) {
        RouteSessionState sessionState = adjustmentResult.sessionState();
        RouteChangeRecord latestChange = latestChange(sessionState);
        return """
                presentation_type: ADJUSTMENT_RESULT
                adjustment_status: %s
                adjustment_message: %s
                latest_change_type: %s
                locked_stop_orders: %s
                route:
                %s
                """.formatted(
                adjustmentResult.status(),
                safe(adjustmentResult.message()),
                latestChange == null ? "null" : latestChange.changeType(),
                sortedLockedStops(sessionState.lockedStopOrders()),
                summarizeRoute(adjustmentResult.adjustedRoute())
        );
    }

    private String buildClarificationPrompt(RouteSessionState routeSessionState) {
        PendingClarification clarification = routeSessionState.pendingClarification();
        return """
                presentation_type: CLARIFICATION
                clarification_question: %s
                candidate_targets: %s
                locked_stop_orders: %s
                current_route_summary:
                %s
                """.formatted(
                clarification.question(),
                clarification.candidateTargets(),
                sortedLockedStops(routeSessionState.lockedStopOrders()),
                routeSessionState.currentRoute() == null ? "null" : summarizeRoute(routeSessionState.currentRoute())
        );
    }

    private String buildNoFeasibleRoutePrompt(RoutePlanRequest routePlanRequest) {
        return """
                presentation_type: NO_FEASIBLE_ROUTE
                scene: %s
                business_area: %s
                district: %s
                time_window: %s
                budget_total: %s
                party_size: %s
                pace: %s
                prefer_tags: %s
                avoid_tags: %s
                """.formatted(
                safe(routePlanRequest.scene()),
                safe(routePlanRequest.businessArea()),
                safe(routePlanRequest.district()),
                safe(routePlanRequest.timeWindow()),
                routePlanRequest.budgetTotal(),
                routePlanRequest.partySize(),
                safe(routePlanRequest.pace()),
                routePlanRequest.preferTags(),
                routePlanRequest.avoidTags()
        );
    }

    private String summarizeRoute(GeneratedRoutePlan routePlan) {
        if (routePlan == null) {
            return "null";
        }
        return """
                scene: %s
                time_window: %s
                total_budget: %s
                total_duration_minutes: %s
                total_distance_km: %s
                start_time: %s
                end_time: %s
                stops:
                %s
                validation_issues:
                %s
                """.formatted(
                safe(routePlan.scene()),
                safe(routePlan.timeWindow()),
                routePlan.totalBudget(),
                routePlan.totalDurationMinutes(),
                routePlan.totalDistanceKm(),
                safe(routePlan.startTime()),
                safe(routePlan.endTime()),
                summarizeStops(routePlan.stops()),
                summarizeValidationIssues(routePlan)
        );
    }

    private String summarizeStops(List<GeneratedRouteStop> stops) {
        return stops.stream()
                .map(stop -> "- stop_order=%s, stop_name=%s, slot_role=%s, business_area=%s, arrive_time=%s, leave_time=%s, matched_prefer_tags=%s, matched_avoid_tags=%s"
                        .formatted(
                                stop.stopOrder(),
                                safe(stop.poiName()),
                                safe(stop.slotRole()),
                                safe(stop.businessArea()),
                                safe(stop.arriveTime()),
                                safe(stop.leaveTime()),
                                stop.matchedPreferTags(),
                                stop.matchedAvoidTags()
                        ))
                .collect(Collectors.joining("\n"));
    }

    private String summarizeValidationIssues(GeneratedRoutePlan routePlan) {
        if (routePlan.validationResult() == null || routePlan.validationResult().issues().isEmpty()) {
            return "none";
        }
        return routePlan.validationResult().issues().stream()
                .map(this::summarizeValidationIssue)
                .collect(Collectors.joining("\n"));
    }

    private String summarizeValidationIssue(RouteValidationIssue issue) {
        return "- code=%s, message=%s, stop_order=%s".formatted(
                safe(issue.code()),
                safe(issue.message()),
                issue.stopOrder()
        );
    }

    private RouteChangeRecord latestChange(RouteSessionState sessionState) {
        if (sessionState == null || sessionState.changeHistory().isEmpty()) {
            return null;
        }
        return sessionState.changeHistory().stream()
                .max(Comparator.comparing(RouteChangeRecord::createdAt))
                .orElse(null);
    }

    private List<Integer> sortedLockedStops(Set<Integer> lockedStopOrders) {
        return lockedStopOrders == null ? List.of() : lockedStopOrders.stream().sorted().toList();
    }

    private String safe(String value) {
        return value == null ? "null" : value;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @FunctionalInterface
    private interface FallbackSupplier {
        String get();
    }
}
