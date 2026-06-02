package com.mtai.mtairouteplanner.controller;

import com.mtai.mtairouteplanner.ai.intent.IntentAgentService;
import com.mtai.mtairouteplanner.ai.presenter.PresenterAgentService;
import com.mtai.mtairouteplanner.controller.dto.NaturalLanguageRouteRequest;
import com.mtai.mtairouteplanner.controller.dto.NaturalLanguageRouteResponse;
import com.mtai.mtairouteplanner.controller.dto.RouteSessionResponse;
import com.mtai.mtairouteplanner.event.service.RouteLifecycleEventService;
import com.mtai.mtairouteplanner.model.adjustment.AdjustmentResult;
import com.mtai.mtairouteplanner.model.adjustment.AdjustmentStatus;
import com.mtai.mtairouteplanner.model.adjustment.ChangeType;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
@RequestMapping("/api/routes")
public class RoutePlanningController {

    private static final Logger log = LoggerFactory.getLogger(RoutePlanningController.class);

    private final IntentAgentService intentAgentService;
    private final RouteOptimizerService routeOptimizerService;
    private final RouteSessionService routeSessionService;
    private final RouteAdjustmentService routeAdjustmentService;
    private final RouteContextAssembler routeContextAssembler;
    private final ClarificationService clarificationService;
    private final PresenterAgentService presenterAgentService;
    private final RouteLifecycleEventService routeLifecycleEventService;

    public RoutePlanningController(
            IntentAgentService intentAgentService,
            RouteOptimizerService routeOptimizerService,
            RouteSessionService routeSessionService,
            RouteAdjustmentService routeAdjustmentService,
            RouteContextAssembler routeContextAssembler,
            ClarificationService clarificationService,
            PresenterAgentService presenterAgentService,
            RouteLifecycleEventService routeLifecycleEventService
    ) {
        this.intentAgentService = intentAgentService;
        this.routeOptimizerService = routeOptimizerService;
        this.routeSessionService = routeSessionService;
        this.routeAdjustmentService = routeAdjustmentService;
        this.routeContextAssembler = routeContextAssembler;
        this.clarificationService = clarificationService;
        this.presenterAgentService = presenterAgentService;
        this.routeLifecycleEventService = routeLifecycleEventService;
    }

    @PostMapping("/plan")
    public ResponseEntity<NaturalLanguageRouteResponse> plan(@RequestBody NaturalLanguageRouteRequest request) {
        validateRequest(request);
        IntentAgentService.PlanParseResult planParseResult = intentAgentService.parsePlanRequestResult(
                request.userId(),
                request.message()
        );
        RoutePlanRequest routePlanRequest = planParseResult.primaryRequest();
        List<GeneratedRoutePlan> routes = routeOptimizerService.generateRoutes(routePlanRequest);
        if (routes.isEmpty() && planParseResult.hasDistinctFallback()) {
            log.info("Primary Spring AI parsed request produced no feasible route. Retrying with fallback request. {}",
                    planParseResult.diagnosticSummary());
            RoutePlanRequest fallbackRequest = planParseResult.fallbackRequest();
            List<GeneratedRoutePlan> fallbackRoutes = routeOptimizerService.generateRoutes(fallbackRequest);
            if (!fallbackRoutes.isEmpty()) {
                routePlanRequest = fallbackRequest;
                routes = fallbackRoutes;
            }
        }
        if (routes.isEmpty()) {
            routeLifecycleEventService.publishRoutePlanFailed(
                    request.userId(),
                    routePlanRequest,
                    "No feasible route found for the natural-language planning request."
            );
            return ResponseEntity.ok(NaturalLanguageRouteResponse.failure(
                    "FAILED",
                    presenterAgentService.presentNoFeasibleRoute(routePlanRequest)
            ));
        }

        GeneratedRoutePlan bestRoute = routes.getFirst();
        RouteSessionState routeSessionState = routeSessionService.createSession(
                request.userId(),
                RouteSessionIntent.from(routePlanRequest),
                bestRoute
        );
        routeLifecycleEventService.publishRoutePlanned(routeSessionState);

        return ResponseEntity.ok(NaturalLanguageRouteResponse.success(
                routeSessionState.sessionId(),
                "SUCCESS",
                bestRoute,
                presenterAgentService.presentInitialRoute(routeSessionState),
                routeSessionState
        ));
    }

    @PostMapping("/{sessionId}/adjust")
    public ResponseEntity<NaturalLanguageRouteResponse> adjust(
            @PathVariable String sessionId,
            @RequestBody NaturalLanguageRouteRequest request
    ) {
        validateRequest(request);
        RouteSessionState session = routeSessionService.findSession(sessionId)
                .orElseThrow(() -> new RouteSessionNotFoundException(sessionId));
        if (!session.userId().equals(request.userId())) {
            throw new IllegalArgumentException("user_id does not match the session owner.");
        }

        CompactRouteContext routeContext = routeContextAssembler.assemble(session);
        IntentAgentService.ParsedAdjustment parsedAdjustment = intentAgentService.parseAdjustment(
                request.message(),
                routeContext
        );

        if (parsedAdjustment.isClarificationAnswer()) {
            ClarificationResolutionResult resolutionResult = clarificationService.resolvePendingClarification(
                    sessionId,
                    parsedAdjustment.clarificationAnswer()
            );
            routeLifecycleEventService.publishClarificationResolved(resolutionResult);
            RouteSessionState latestSession = routeSessionService.findSession(sessionId)
                    .orElseThrow(() -> new RouteSessionNotFoundException(sessionId));
            AdjustmentResult adjustmentResult = routeAdjustmentService.applyChange(
                    sessionId,
                    latestSession.version(),
                    resolutionResult.resolvedChangeRequest()
            );
            publishAdjustmentLifecycleEvent(adjustmentResult, resolutionResult.resolvedChangeRequest().changeType());
            return toResponse(adjustmentResult, "Clarification resolved and route updated.");
        }

        AdjustmentResult adjustmentResult = routeAdjustmentService.applyChange(
                sessionId,
                session.version(),
                parsedAdjustment.changeRequest()
        );
        publishAdjustmentLifecycleEvent(
                adjustmentResult,
                parsedAdjustment.changeRequest() == null ? null : parsedAdjustment.changeRequest().changeType()
        );

        if (adjustmentResult.status() == AdjustmentStatus.NOT_FOUND) {
            throw new RouteSessionNotFoundException(sessionId);
        }
        if (adjustmentResult.status() == AdjustmentStatus.VERSION_CONFLICT) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(toNaturalLanguageResponse(adjustmentResult, adjustmentResult.message()));
        }
        return ResponseEntity.ok(toNaturalLanguageResponse(adjustmentResult, adjustmentResult.message()));
    }

    @GetMapping("/{sessionId}")
    public RouteSessionResponse getSession(@PathVariable String sessionId) {
        return routeSessionService.findSession(sessionId)
                .map(RouteSessionResponse::from)
                .orElseThrow(() -> new RouteSessionNotFoundException(sessionId));
    }

    private ResponseEntity<NaturalLanguageRouteResponse> toResponse(AdjustmentResult adjustmentResult, String defaultMessage) {
        return ResponseEntity.ok(toNaturalLanguageResponse(adjustmentResult, defaultMessage));
    }

    private NaturalLanguageRouteResponse toNaturalLanguageResponse(AdjustmentResult adjustmentResult, String defaultMessage) {
        RouteSessionState sessionState = adjustmentResult.sessionState();
        String presenterMessage = adjustmentResult.status() == AdjustmentStatus.WAITING_CLARIFICATION
                ? presenterAgentService.presentClarification(sessionState)
                : presenterAgentService.presentAdjustmentResult(adjustmentResult);
        return new NaturalLanguageRouteResponse(
                adjustmentResult.sessionId(),
                adjustmentResult.status().name(),
                adjustmentResult.adjustedRoute(),
                hasText(presenterMessage) ? presenterMessage : (adjustmentResult.message() == null ? defaultMessage : adjustmentResult.message()),
                sessionState == null ? null : RouteSessionResponse.from(sessionState)
        );
    }

    private void validateRequest(NaturalLanguageRouteRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required.");
        }
        if (request.userId() == null || request.userId().isBlank()) {
            throw new IllegalArgumentException("user_id is required.");
        }
        if (request.message() == null || request.message().isBlank()) {
            throw new IllegalArgumentException("message is required.");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private void publishAdjustmentLifecycleEvent(AdjustmentResult adjustmentResult, ChangeType changeType) {
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

