package com.mtai.mtairouteplanner.service;

import com.mtai.mtairouteplanner.model.ChangeRequest;
import com.mtai.mtairouteplanner.model.ChangeType;
import com.mtai.mtairouteplanner.model.ClarificationAnswer;
import com.mtai.mtairouteplanner.model.ClarificationResolutionResult;
import com.mtai.mtairouteplanner.model.CompactRouteContext;
import com.mtai.mtairouteplanner.model.GeneratedRoutePlan;
import com.mtai.mtairouteplanner.model.RouteChangeRecord;
import com.mtai.mtairouteplanner.model.RoutePlanRequest;
import com.mtai.mtairouteplanner.model.RouteSessionIntent;
import com.mtai.mtairouteplanner.model.RouteSessionState;
import com.mtai.mtairouteplanner.model.RouteSessionStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RouteContextAssemblerAndClarificationServiceTest {

    private final RouteSessionService routeSessionService = new RouteSessionService();
    private final RouteOptimizerService routeOptimizerService = new RouteOptimizerService();
    private final RouteAdjustmentService routeAdjustmentService = new RouteAdjustmentService(routeSessionService);
    private final RouteContextAssembler routeContextAssembler = new RouteContextAssembler();
    private final ClarificationService clarificationService = new ClarificationService(routeSessionService);

    @Test
    void contextAssemblerReturnsCompactRouteAndLockedStops() {
        RouteSessionState session = createCitywalkSession();
        RouteSessionState locked = routeSessionService.lockStop(session.sessionId(), 2);

        for (int i = 1; i <= 6; i++) {
            routeSessionService.appendChangeHistory(
                    session.sessionId(),
                    new RouteChangeRecord(
                            "C10" + i,
                            "TEST_CHANGE_" + i,
                            "change-" + i,
                            i % locked.currentRoute().stops().size() + 1,
                            locked.currentRoute(),
                            locked.currentRoute(),
                            LocalDateTime.of(2026, 5, 28, 12, i)
                    )
            );
        }

        RouteSessionState latest = routeSessionService.findSession(session.sessionId()).orElseThrow();
        CompactRouteContext context = routeContextAssembler.assemble(latest);

        assertThat(context.sessionId()).isEqualTo(session.sessionId());
        assertThat(context.lockedStopOrders()).containsExactly(2);
        assertThat(context.currentRouteStops()).hasSize(latest.currentRoute().stops().size());
        assertThat(context.currentRouteSummary()).contains("Citywalk");
        assertThat(Arrays.stream(context.currentRouteStops().getFirst().getClass().getRecordComponents())
                .map(component -> component.getName())
                .toList()).doesNotContain("matchedPreferTags", "matchedAvoidTags", "validationResult");
        assertThat(context.latestChangeHistory()).hasSize(5);
        assertThat(context.latestChangeHistory()).extracting(item -> item.changeId())
                .containsExactly("C102", "C103", "C104", "C105", "C106");
    }

    @Test
    void pendingClarificationSetsWaitingStatusAndKeepsRouteUnchanged() {
        RouteSessionState session = createCitywalkSession();
        GeneratedRoutePlan originalRoute = session.currentRoute();

        var adjustmentResult = routeAdjustmentService.applyChange(
                session.sessionId(),
                session.version(),
                new ChangeRequest(ChangeType.REPLACE_STOP, null, null, null, null, List.of(), List.of(), List.of())
        );

        assertThat(adjustmentResult.status().name()).isEqualTo("WAITING_CLARIFICATION");
        assertThat(adjustmentResult.sessionState().status()).isEqualTo(RouteSessionStatus.WAITING_CLARIFICATION);
        assertThat(adjustmentResult.sessionState().pendingClarification()).isNotNull();
        assertThat(adjustmentResult.sessionState().currentRoute()).isEqualTo(originalRoute);
    }

    @Test
    void answeringClarificationResolvesTargetStopAndClearsPendingState() {
        RouteSessionState session = createCitywalkSession();
        routeAdjustmentService.applyChange(
                session.sessionId(),
                session.version(),
                new ChangeRequest(ChangeType.REPLACE_STOP, null, null, null, null, List.of(), List.of(), List.of())
        );

        ClarificationResolutionResult resolutionResult = clarificationService.resolvePendingClarification(
                session.sessionId(),
                new ClarificationAnswer(2, null)
        );

        assertThat(resolutionResult.resolvedChangeRequest()).isNotNull();
        assertThat(resolutionResult.resolvedChangeRequest().targetStopOrder()).isEqualTo(2);
        assertThat(resolutionResult.sessionState().status()).isEqualTo(RouteSessionStatus.ACTIVE);
        assertThat(resolutionResult.sessionState().pendingClarification()).isNull();
        assertThat(resolutionResult.adjustedRoute()).isEqualTo(session.currentRoute());
    }

    private RouteSessionState createCitywalkSession() {
        RoutePlanRequest request = new RoutePlanRequest(
                "U10003",
                "Citywalk",
                null,
                "东城区",
                "13:00-22:00",
                800,
                2,
                "适中",
                List.of("适合拍照"),
                List.of()
        );
        GeneratedRoutePlan route = routeOptimizerService.generateRoutes(request).getFirst();
        return routeSessionService.createSession(request.userId(), RouteSessionIntent.from(request), route);
    }
}
