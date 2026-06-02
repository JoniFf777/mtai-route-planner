package com.mtai.mtairouteplanner.service.route.context;

import com.mtai.mtairouteplanner.model.adjustment.ChangeRequest;
import com.mtai.mtairouteplanner.model.adjustment.ChangeType;
import com.mtai.mtairouteplanner.model.clarification.ClarificationAnswer;
import com.mtai.mtairouteplanner.model.clarification.ClarificationResolutionResult;
import com.mtai.mtairouteplanner.model.context.CompactRouteContext;
import com.mtai.mtairouteplanner.model.route.GeneratedRoutePlan;
import com.mtai.mtairouteplanner.model.adjustment.RouteChangeRecord;
import com.mtai.mtairouteplanner.model.route.RoutePlanRequest;
import com.mtai.mtairouteplanner.model.session.RouteSessionIntent;
import com.mtai.mtairouteplanner.model.session.RouteSessionState;
import com.mtai.mtairouteplanner.model.session.RouteSessionStatus;
import org.junit.jupiter.api.Test;

import com.mtai.mtairouteplanner.service.route.adjustment.RouteAdjustmentService;
import com.mtai.mtairouteplanner.service.route.clarification.ClarificationService;
import com.mtai.mtairouteplanner.service.route.planning.RouteOptimizerService;
import com.mtai.mtairouteplanner.service.route.session.InMemoryRouteSessionStore;
import com.mtai.mtairouteplanner.service.route.session.RouteSessionService;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RouteContextAssemblerAndClarificationServiceTest {

    private final RouteSessionService routeSessionService = new RouteSessionService(new InMemoryRouteSessionStore());
    private final RouteOptimizerService routeOptimizerService = new RouteOptimizerService();
    private final RouteAdjustmentService routeAdjustmentService = new RouteAdjustmentService(routeSessionService);
    private final RouteContextAssembler routeContextAssembler = new RouteContextAssembler();
    private final ClarificationService clarificationService = new ClarificationService(routeSessionService);

    @Test
    void contextAssemblerReturnsCompactRouteAndLockedStops() {
        RouteSessionState session = createCitywalkSession();
        RouteSessionState latest = routeSessionService.lockStop(session.sessionId(), session.version(), 2);

        for (int i = 1; i <= 6; i++) {
            latest = routeSessionService.appendChangeHistory(
                    latest.sessionId(),
                    latest.version(),
                    new RouteChangeRecord(
                            "C10" + i,
                            "TEST_CHANGE_" + i,
                            "change-" + i,
                            i % latest.currentRoute().stops().size() + 1,
                            latest.currentRoute(),
                            latest.currentRoute(),
                            LocalDateTime.of(2026, 5, 28, 12, i)
                    )
            );
        }

        latest = routeSessionService.findSession(session.sessionId()).orElseThrow();
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
