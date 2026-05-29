package com.mtai.mtairouteplanner.service;

import com.mtai.mtairouteplanner.model.AdjustmentResult;
import com.mtai.mtairouteplanner.model.AdjustmentStatus;
import com.mtai.mtairouteplanner.model.ChangeRequest;
import com.mtai.mtairouteplanner.model.ChangeType;
import com.mtai.mtairouteplanner.model.GeneratedRoutePlan;
import com.mtai.mtairouteplanner.model.RoutePlanRequest;
import com.mtai.mtairouteplanner.model.RouteSessionIntent;
import com.mtai.mtairouteplanner.model.RouteSessionState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RouteAdjustmentServiceTest {

    private final RouteSessionService routeSessionService = new RouteSessionService();
    private final RouteOptimizerService routeOptimizerService = new RouteOptimizerService();
    private final RouteAdjustmentService routeAdjustmentService = new RouteAdjustmentService(routeSessionService);

    @Test
    void replaceStopChangesTheTargetStop() {
        RouteSessionState session = createCitywalkSession();
        String originalPoiId = session.currentRoute().stops().get(0).poiId();

        AdjustmentResult result = routeAdjustmentService.applyChange(
                session.sessionId(),
                session.version(),
                new ChangeRequest(ChangeType.REPLACE_STOP, 1, null, null, null, List.of(), List.of(), List.of())
        );

        assertThat(result.status()).isEqualTo(AdjustmentStatus.SUCCESS);
        assertThat(result.adjustedRoute().stops().get(0).poiId()).isNotEqualTo(originalPoiId);
    }

    @Test
    void removeStopReducesRouteLength() {
        RouteSessionState session = createCitywalkSession();

        AdjustmentResult result = routeAdjustmentService.applyChange(
                session.sessionId(),
                session.version(),
                new ChangeRequest(ChangeType.REMOVE_STOP, 4, null, null, null, List.of(), List.of(), List.of())
        );

        assertThat(result.status()).isEqualTo(AdjustmentStatus.SUCCESS);
        assertThat(result.adjustedRoute().stops()).hasSize(session.currentRoute().stops().size() - 1);
    }

    @Test
    void addStopIncreasesRouteLengthIfFeasible() {
        RouteSessionState session = createCitywalkSession();

        AdjustmentResult result = routeAdjustmentService.applyChange(
                session.sessionId(),
                session.version(),
                new ChangeRequest(ChangeType.ADD_STOP, 4, "甜品收尾", null, null, List.of(), List.of(), List.of())
        );

        assertThat(result.status()).isEqualTo(AdjustmentStatus.SUCCESS);
        assertThat(result.adjustedRoute().stops()).hasSize(session.currentRoute().stops().size() + 1);
    }

    @Test
    void lowerBudgetUpdatesIntentAndProducesCheaperRoute() {
        RouteSessionState session = createDatingSession();

        AdjustmentResult result = routeAdjustmentService.applyChange(
                session.sessionId(),
                session.version(),
                new ChangeRequest(ChangeType.LOWER_BUDGET, null, null, 650, null, List.of(), List.of(), List.of())
        );

        assertThat(result.status()).isEqualTo(AdjustmentStatus.SUCCESS);
        assertThat(result.sessionState().currentIntent().budgetTotal()).isEqualTo(650);
        assertThat(result.adjustedRoute().totalBudget()).isLessThan(session.currentRoute().totalBudget());
    }

    @Test
    void switchToIndoorProducesIndoorRoute() {
        RouteSessionState session = createCitywalkSession();

        AdjustmentResult result = routeAdjustmentService.applyChange(
                session.sessionId(),
                session.version(),
                new ChangeRequest(ChangeType.SWITCH_TO_INDOOR, null, null, null, null, List.of(), List.of(), List.of())
        );

        assertThat(result.status()).isEqualTo(AdjustmentStatus.SUCCESS);
        assertThat(result.sessionState().currentIntent().scene()).isEqualTo("雨天路线");
        assertThat(result.adjustedRoute().stops()).allSatisfy(stop ->
                assertThat(stop.indoorOutdoor()).isEqualToIgnoringCase("indoor"));
    }

    @Test
    void lowerBudgetWithoutLockedStopsReplansDatingRouteOrReturnsSpecificBudgetReason() {
        RouteSessionState session = createApiDatingSession();

        AdjustmentResult result = routeAdjustmentService.applyChange(
                session.sessionId(),
                session.version(),
                new ChangeRequest(ChangeType.LOWER_BUDGET, null, null, 300, null, List.of(), List.of(), List.of())
        );

        if (result.status() == AdjustmentStatus.SUCCESS) {
            assertThat(result.sessionState().lockedStopOrders()).isEmpty();
            assertThat(result.sessionState().currentIntent().budgetTotal()).isEqualTo(300);
            assertThat(result.adjustedRoute().totalBudget()).isLessThanOrEqualTo(300);
            assertThat(result.sessionState().changeHistory()).isNotEmpty();
        } else {
            assertThat(result.status()).isEqualTo(AdjustmentStatus.FAILED);
            assertThat(result.message()).containsIgnoringCase("budget");
            assertThat(result.message()).doesNotContain("No feasible adjusted route found");
            assertThat(result.sessionState().lockedStopOrders()).isEmpty();
        }
    }

    @Test
    void switchToIndoorWithoutLockedStopsFindsIndoorFriendlyFallbackForDatingRoute() {
        RouteSessionState session = createApiDatingSession();

        AdjustmentResult result = routeAdjustmentService.applyChange(
                session.sessionId(),
                session.version(),
                new ChangeRequest(ChangeType.SWITCH_TO_INDOOR, null, null, null, null, List.of(), List.of(), List.of())
        );

        assertThat(result.status()).isEqualTo(AdjustmentStatus.SUCCESS);
        assertThat(result.sessionState().lockedStopOrders()).isEmpty();
        assertThat(result.sessionState().currentIntent().scene()).isEqualTo("雨天路线");
        assertThat(result.adjustedRoute().stops()).allSatisfy(stop ->
                assertThat(stop.indoorOutdoor()).isEqualToIgnoringCase("indoor"));
        assertThat(result.sessionState().changeHistory()).isNotEmpty();
    }

    @Test
    void lockStopPreventsChangingThatStop() {
        RouteSessionState session = createCitywalkSession();
        AdjustmentResult locked = routeAdjustmentService.applyChange(
                session.sessionId(),
                session.version(),
                new ChangeRequest(ChangeType.LOCK_STOP, 2, null, null, null, List.of(), List.of(), List.of())
        );

        AdjustmentResult rejected = routeAdjustmentService.applyChange(
                session.sessionId(),
                locked.sessionState().version(),
                new ChangeRequest(ChangeType.REPLACE_STOP, 2, null, null, null, List.of(), List.of(), List.of())
        );

        assertThat(locked.status()).isEqualTo(AdjustmentStatus.SUCCESS);
        assertThat(rejected.status()).isEqualTo(AdjustmentStatus.REJECTED);
        assertThat(rejected.message()).contains("locked");
    }

    @Test
    void unlockStopAllowsChangingItLater() {
        RouteSessionState session = createCitywalkSession();
        AdjustmentResult locked = routeAdjustmentService.applyChange(
                session.sessionId(),
                session.version(),
                new ChangeRequest(ChangeType.LOCK_STOP, 2, null, null, null, List.of(), List.of(), List.of())
        );
        String originalPoiId = locked.sessionState().currentRoute().stops().get(1).poiId();

        AdjustmentResult unlocked = routeAdjustmentService.applyChange(
                session.sessionId(),
                locked.sessionState().version(),
                new ChangeRequest(ChangeType.UNLOCK_STOP, 2, null, null, null, List.of(), List.of(), List.of())
        );
        AdjustmentResult replaced = routeAdjustmentService.applyChange(
                session.sessionId(),
                unlocked.sessionState().version(),
                new ChangeRequest(ChangeType.REPLACE_STOP, 2, null, null, null, List.of(), List.of(), List.of())
        );

        assertThat(unlocked.status()).isEqualTo(AdjustmentStatus.SUCCESS);
        assertThat(replaced.status()).isEqualTo(AdjustmentStatus.SUCCESS);
        assertThat(replaced.adjustedRoute().stops().get(1).poiId()).isNotEqualTo(originalPoiId);
    }

    @Test
    void changeHistoryIsAppended() {
        RouteSessionState session = createDatingSession();

        AdjustmentResult result = routeAdjustmentService.applyChange(
                session.sessionId(),
                session.version(),
                new ChangeRequest(ChangeType.LOWER_BUDGET, null, null, 650, null, List.of(), List.of(), List.of())
        );

        assertThat(result.status()).isEqualTo(AdjustmentStatus.SUCCESS);
        assertThat(result.sessionState().changeHistory()).hasSize(1);
        assertThat(result.sessionState().changeHistory().getFirst().changeType()).isEqualTo(ChangeType.LOWER_BUDGET.name());
    }

    @Test
    void infeasibleChangeReturnsSafeFailureResult() {
        RouteSessionState session = createDatingSession();

        AdjustmentResult result = routeAdjustmentService.applyChange(
                session.sessionId(),
                session.version(),
                new ChangeRequest(ChangeType.CHANGE_TIME_WINDOW, null, null, null, "18:00-18:20", List.of(), List.of(), List.of())
        );

        assertThat(result.status()).isEqualTo(AdjustmentStatus.FAILED);
        assertThat(result.adjustedRoute()).isEqualTo(session.currentRoute());
    }

    private RouteSessionState createDatingSession() {
        RoutePlanRequest request = new RoutePlanRequest(
                "U10001",
                "情侣约会",
                null,
                "朝阳区",
                "16:00-23:00",
                1200,
                2,
                "轻松",
                List.of("安静"),
                List.of()
        );
        return createSession(request);
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
        return createSession(request);
    }

    private RouteSessionState createRainySession() {
        RoutePlanRequest request = new RoutePlanRequest(
                "U10004",
                "雨天路线",
                null,
                "朝阳区",
                "13:00-21:00",
                1000,
                2,
                "轻松",
                List.of("展览"),
                List.of()
        );
        return createSession(request);
    }

    private RouteSessionState createApiDatingSession() {
        RoutePlanRequest request = new RoutePlanRequest(
                "U10001",
                "情侣约会",
                null,
                "朝阳区",
                "18:00-22:00",
                500,
                2,
                "轻松",
                List.of("拍照"),
                List.of()
        );
        return createSession(request);
    }

    private RouteSessionState createSession(RoutePlanRequest request) {
        GeneratedRoutePlan route = routeOptimizerService.generateRoutes(request).getFirst();
        return routeSessionService.createSession(request.userId(), RouteSessionIntent.from(request), route);
    }
}
