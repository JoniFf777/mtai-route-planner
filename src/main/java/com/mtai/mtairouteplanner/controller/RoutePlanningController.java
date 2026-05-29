package com.mtai.mtairouteplanner.controller;

import com.mtai.mtairouteplanner.ai.FakeIntentAgentService;
import com.mtai.mtairouteplanner.ai.FakePresenterService;
import com.mtai.mtairouteplanner.controller.dto.NaturalLanguageRouteRequest;
import com.mtai.mtairouteplanner.controller.dto.NaturalLanguageRouteResponse;
import com.mtai.mtairouteplanner.controller.dto.RouteSessionResponse;
import com.mtai.mtairouteplanner.model.AdjustmentResult;
import com.mtai.mtairouteplanner.model.AdjustmentStatus;
import com.mtai.mtairouteplanner.model.ClarificationResolutionResult;
import com.mtai.mtairouteplanner.model.CompactRouteContext;
import com.mtai.mtairouteplanner.model.GeneratedRoutePlan;
import com.mtai.mtairouteplanner.model.RoutePlanRequest;
import com.mtai.mtairouteplanner.model.RouteSessionIntent;
import com.mtai.mtairouteplanner.model.RouteSessionState;
import com.mtai.mtairouteplanner.service.ClarificationService;
import com.mtai.mtairouteplanner.service.RouteAdjustmentService;
import com.mtai.mtairouteplanner.service.RouteContextAssembler;
import com.mtai.mtairouteplanner.service.RouteOptimizerService;
import com.mtai.mtairouteplanner.service.RouteSessionNotFoundException;
import com.mtai.mtairouteplanner.service.RouteSessionService;
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

    private final FakeIntentAgentService fakeIntentAgentService;
    private final RouteOptimizerService routeOptimizerService;
    private final RouteSessionService routeSessionService;
    private final RouteAdjustmentService routeAdjustmentService;
    private final RouteContextAssembler routeContextAssembler;
    private final ClarificationService clarificationService;
    private final FakePresenterService fakePresenterService;

    public RoutePlanningController(
            FakeIntentAgentService fakeIntentAgentService,
            RouteOptimizerService routeOptimizerService,
            RouteSessionService routeSessionService,
            RouteAdjustmentService routeAdjustmentService,
            RouteContextAssembler routeContextAssembler,
            ClarificationService clarificationService,
            FakePresenterService fakePresenterService
    ) {
        this.fakeIntentAgentService = fakeIntentAgentService;
        this.routeOptimizerService = routeOptimizerService;
        this.routeSessionService = routeSessionService;
        this.routeAdjustmentService = routeAdjustmentService;
        this.routeContextAssembler = routeContextAssembler;
        this.clarificationService = clarificationService;
        this.fakePresenterService = fakePresenterService;
    }

    @PostMapping("/plan")
    public ResponseEntity<NaturalLanguageRouteResponse> plan(@RequestBody NaturalLanguageRouteRequest request) {
        validateRequest(request);
        RoutePlanRequest routePlanRequest = fakeIntentAgentService.parsePlanRequest(request.userId(), request.message());
        List<GeneratedRoutePlan> routes = routeOptimizerService.generateRoutes(routePlanRequest);
        if (routes.isEmpty()) {
            return ResponseEntity.ok(NaturalLanguageRouteResponse.failure(
                    "FAILED",
                    fakePresenterService.presentNoFeasibleRoute(routePlanRequest)
            ));
        }

        GeneratedRoutePlan bestRoute = routes.getFirst();
        RouteSessionState routeSessionState = routeSessionService.createSession(
                request.userId(),
                RouteSessionIntent.from(routePlanRequest),
                bestRoute
        );

        return ResponseEntity.ok(NaturalLanguageRouteResponse.success(
                routeSessionState.sessionId(),
                "SUCCESS",
                bestRoute,
                fakePresenterService.presentPlanSuccess(routeSessionState),
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
        FakeIntentAgentService.ParsedAdjustment parsedAdjustment = fakeIntentAgentService.parseAdjustment(
                request.message(),
                routeContext
        );

        if (parsedAdjustment.isClarificationAnswer()) {
            ClarificationResolutionResult resolutionResult = clarificationService.resolvePendingClarification(
                    sessionId,
                    parsedAdjustment.clarificationAnswer()
            );
            RouteSessionState latestSession = routeSessionService.findSession(sessionId)
                    .orElseThrow(() -> new RouteSessionNotFoundException(sessionId));
            AdjustmentResult adjustmentResult = routeAdjustmentService.applyChange(
                    sessionId,
                    latestSession.version(),
                    resolutionResult.resolvedChangeRequest()
            );
            return toResponse(adjustmentResult, "Clarification resolved and route updated.");
        }

        AdjustmentResult adjustmentResult = routeAdjustmentService.applyChange(
                sessionId,
                session.version(),
                parsedAdjustment.changeRequest()
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
        String presenterMessage = fakePresenterService.presentAdjustmentResult(adjustmentResult);
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
}
