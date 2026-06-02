package com.mtai.mtairouteplanner.controller;

import com.mtai.mtairouteplanner.controller.dto.RouteSessionResponse;
import com.mtai.mtairouteplanner.controller.dto.StructuredRoutePlanRequest;
import com.mtai.mtairouteplanner.controller.dto.StructuredRoutePlanResponse;
import com.mtai.mtairouteplanner.event.service.RouteLifecycleEventService;
import com.mtai.mtairouteplanner.model.adjustment.AdjustmentResult;
import com.mtai.mtairouteplanner.model.adjustment.AdjustmentStatus;
import com.mtai.mtairouteplanner.model.adjustment.ChangeRequest;
import com.mtai.mtairouteplanner.model.clarification.ClarificationAnswer;
import com.mtai.mtairouteplanner.model.clarification.ClarificationResolutionResult;
import com.mtai.mtairouteplanner.model.context.CompactRouteContext;
import com.mtai.mtairouteplanner.model.route.GeneratedRoutePlan;
import com.mtai.mtairouteplanner.model.route.RoutePlanRequest;
import com.mtai.mtairouteplanner.model.session.RouteSessionIntent;
import com.mtai.mtairouteplanner.model.session.RouteSessionState;
import com.mtai.mtairouteplanner.service.route.clarification.ClarificationService;
import com.mtai.mtairouteplanner.service.route.adjustment.RouteAdjustmentService;
import com.mtai.mtairouteplanner.service.route.context.RouteContextAssembler;
import com.mtai.mtairouteplanner.service.route.planning.RouteOptimizerService;
import com.mtai.mtairouteplanner.service.route.session.RouteSessionNotFoundException;
import com.mtai.mtairouteplanner.service.route.session.RouteSessionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/dev/routes")
public class DevRouteController {

    private final RouteOptimizerService routeOptimizerService;
    private final RouteSessionService routeSessionService;
    private final RouteAdjustmentService routeAdjustmentService;
    private final RouteContextAssembler routeContextAssembler;
    private final ClarificationService clarificationService;
    private final RouteLifecycleEventService routeLifecycleEventService;

    public DevRouteController(
            RouteOptimizerService routeOptimizerService,
            RouteSessionService routeSessionService,
            RouteAdjustmentService routeAdjustmentService,
            RouteContextAssembler routeContextAssembler,
            ClarificationService clarificationService,
            RouteLifecycleEventService routeLifecycleEventService
    ) {
        this.routeOptimizerService = routeOptimizerService;
        this.routeSessionService = routeSessionService;
        this.routeAdjustmentService = routeAdjustmentService;
        this.routeContextAssembler = routeContextAssembler;
        this.clarificationService = clarificationService;
        this.routeLifecycleEventService = routeLifecycleEventService;
    }

    @PostMapping("/plan-structured")
    public ResponseEntity<StructuredRoutePlanResponse> planStructured(@RequestBody StructuredRoutePlanRequest request) {
        validatePlanRequest(request);
        RoutePlanRequest routePlanRequest = request.toRoutePlanRequest();
        List<GeneratedRoutePlan> routes = routeOptimizerService.generateRoutes(routePlanRequest);
        if (routes.isEmpty()) {
            routeLifecycleEventService.publishRoutePlanFailed(
                    request.userId(),
                    routePlanRequest,
                    "No feasible route found for the structured planning request."
            );
            return ResponseEntity.ok(new StructuredRoutePlanResponse(
                    null,
                    "FAILED",
                    null,
                    "No feasible route found for the structured request."
            ));
        }

        GeneratedRoutePlan bestRoute = routes.getFirst();
        RouteSessionState routeSessionState = routeSessionService.createSession(
                request.userId(),
                RouteSessionIntent.from(routePlanRequest),
                bestRoute
        );
        routeLifecycleEventService.publishRoutePlanned(routeSessionState);

        return ResponseEntity.ok(new StructuredRoutePlanResponse(
                routeSessionState.sessionId(),
                "SUCCESS",
                bestRoute,
                "Structured route planned successfully."
        ));
    }

    @GetMapping("/{sessionId}")
    public RouteSessionResponse getSession(@PathVariable String sessionId) {
        return routeSessionService.findSession(sessionId)
                .map(RouteSessionResponse::from)
                .orElseThrow(() -> new RouteSessionNotFoundException(sessionId));
    }

    @GetMapping("/{sessionId}/context")
    public CompactRouteContext getContext(@PathVariable String sessionId) {
        return routeSessionService.findSession(sessionId)
                .map(routeContextAssembler::assemble)
                .orElseThrow(() -> new RouteSessionNotFoundException(sessionId));
    }

    @PostMapping("/{sessionId}/adjust-structured")
    public ResponseEntity<?> adjustStructured(@PathVariable String sessionId, @RequestBody ChangeRequest changeRequest) {
        validateChangeRequest(changeRequest);
        RouteSessionState session = routeSessionService.findSession(sessionId)
                .orElseThrow(() -> new RouteSessionNotFoundException(sessionId));

        AdjustmentResult adjustmentResult = routeAdjustmentService.applyChange(sessionId, session.version(), changeRequest);
        publishAdjustmentLifecycleEvent(adjustmentResult, changeRequest.changeType());
        if (adjustmentResult.status() == AdjustmentStatus.NOT_FOUND) {
            throw new RouteSessionNotFoundException(sessionId);
        }
        if (adjustmentResult.status() == AdjustmentStatus.VERSION_CONFLICT) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(adjustmentResult);
        }
        return ResponseEntity.ok(adjustmentResult);
    }

    @PostMapping("/{sessionId}/clarification/answer")
    public ResponseEntity<ClarificationResolutionResult> answerClarification(
            @PathVariable String sessionId,
            @RequestBody ClarificationAnswer clarificationAnswer
    ) {
        ClarificationResolutionResult resolutionResult = clarificationService.resolvePendingClarification(sessionId, clarificationAnswer);
        routeLifecycleEventService.publishClarificationResolved(resolutionResult);
        RouteSessionState currentSession = routeSessionService.findSession(sessionId)
                .orElseThrow(() -> new RouteSessionNotFoundException(sessionId));

        AdjustmentResult adjustmentResult = routeAdjustmentService.applyChange(
                sessionId,
                currentSession.version(),
                resolutionResult.resolvedChangeRequest()
        );
        publishAdjustmentLifecycleEvent(adjustmentResult, resolutionResult.resolvedChangeRequest().changeType());

        return ResponseEntity.ok(new ClarificationResolutionResult(
                sessionId,
                adjustmentResult.status().name(),
                adjustmentResult.message(),
                resolutionResult.resolvedChangeRequest(),
                adjustmentResult.sessionState(),
                adjustmentResult.adjustedRoute()
        ));
    }

    private void validatePlanRequest(StructuredRoutePlanRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required.");
        }
        if (!hasText(request.userId())) {
            throw new IllegalArgumentException("user_id is required.");
        }
        if (!hasText(request.scene())) {
            throw new IllegalArgumentException("scene is required.");
        }
        if (!hasText(request.timeWindow()) || !request.timeWindow().contains("-")) {
            throw new IllegalArgumentException("time_window is required and must be in HH:mm-HH:mm format.");
        }
        if (request.budgetTotal() <= 0) {
            throw new IllegalArgumentException("budget_total must be greater than 0.");
        }
        if (request.partySize() <= 0) {
            throw new IllegalArgumentException("party_size must be greater than 0.");
        }
        if (!hasText(request.pace())) {
            throw new IllegalArgumentException("pace is required.");
        }
    }

    private void validateChangeRequest(ChangeRequest changeRequest) {
        if (changeRequest == null || changeRequest.changeType() == null) {
            throw new IllegalArgumentException("change_type is required.");
        }
        switch (changeRequest.changeType()) {
            case LOWER_BUDGET -> {
                if (changeRequest.newBudgetTotal() == null || changeRequest.newBudgetTotal() <= 0) {
                    throw new IllegalArgumentException("new_budget_total must be greater than 0 for LOWER_BUDGET.");
                }
            }
            case CHANGE_TIME_WINDOW -> {
                if (changeRequest.newTimeWindow() == null || !changeRequest.newTimeWindow().contains("-")) {
                    throw new IllegalArgumentException("new_time_window is required for CHANGE_TIME_WINDOW.");
                }
            }
            default -> {
            }
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private void publishAdjustmentLifecycleEvent(AdjustmentResult adjustmentResult, com.mtai.mtairouteplanner.model.adjustment.ChangeType changeType) {
        if (adjustmentResult == null || adjustmentResult.status() == null) {
            return;
        }
        switch (adjustmentResult.status()) {
            case SUCCESS -> routeLifecycleEventService.publishRouteAdjusted(adjustmentResult, changeType);
            case WAITING_CLARIFICATION -> routeLifecycleEventService.publishRouteWaitingClarification(adjustmentResult, changeType);
            case FAILED, REJECTED, VERSION_CONFLICT, NOT_FOUND -> routeLifecycleEventService.publishRouteAdjustmentFailed(adjustmentResult, changeType);
        }
    }
}

