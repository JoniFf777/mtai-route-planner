package com.mtai.mtairouteplanner.service;

import com.mtai.mtairouteplanner.model.GeneratedRoutePlan;
import com.mtai.mtairouteplanner.model.GeneratedRouteStop;
import com.mtai.mtairouteplanner.model.PendingClarification;
import com.mtai.mtairouteplanner.model.RouteChangeRecord;
import com.mtai.mtairouteplanner.model.RoutePlanRequest;
import com.mtai.mtairouteplanner.model.RouteSessionIntent;
import com.mtai.mtairouteplanner.model.RouteSessionState;
import com.mtai.mtairouteplanner.model.RouteSessionStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RouteSessionServiceTest {

    private final RouteSessionService routeSessionService = new RouteSessionService(new InMemoryRouteSessionStore());

    @Test
    void createAndReadSession() {
        RouteSessionIntent intent = RouteSessionIntent.from(sampleRequest());
        GeneratedRoutePlan route = sampleRoute("RT001", "dating");

        RouteSessionState created = routeSessionService.createSession("U10001", intent, route);

        assertThat(created.sessionId()).startsWith("S");
        assertThat(created.userId()).isEqualTo("U10001");
        assertThat(created.status()).isEqualTo(RouteSessionStatus.ACTIVE);
        assertThat(created.currentIntent()).isEqualTo(intent);
        assertThat(created.currentRoute()).isEqualTo(route);
        assertThat(created.version()).isEqualTo(1L);
        assertThat(routeSessionService.findSession(created.sessionId())).contains(created);
    }

    @Test
    void updateRouteIncrementsVersion() {
        RouteSessionState created = routeSessionService.createSession(
                "U10001",
                RouteSessionIntent.from(sampleRequest()),
                sampleRoute("RT001", "dating")
        );

        RouteSessionState updated = routeSessionService.updateCurrentRoute(
                created.sessionId(),
                created.version(),
                sampleRoute("RT020", "dating")
        );

        assertThat(updated.currentRoute().templateId()).isEqualTo("RT020");
        assertThat(updated.version()).isEqualTo(created.version() + 1);
        assertThat(updated.updatedAt()).isAfterOrEqualTo(created.updatedAt());
    }

    @Test
    void versionConflictIsRejected() {
        RouteSessionState created = routeSessionService.createSession(
                "U10001",
                RouteSessionIntent.from(sampleRequest()),
                sampleRoute("RT001", "dating")
        );

        routeSessionService.updateCurrentIntent(
                created.sessionId(),
                created.version(),
                new RouteSessionIntent("Citywalk", null, "dongcheng", "13:00-22:00", 800, 2, "moderate", List.of("photo"), List.of())
        );

        assertThatThrownBy(() -> routeSessionService.updateCurrentRoute(
                created.sessionId(),
                created.version(),
                sampleRoute("RT005", "Citywalk")
        )).isInstanceOf(RouteSessionVersionConflictException.class)
                .hasMessageContaining(created.sessionId());
    }

    @Test
    void lockUnlockStopWorks() {
        RouteSessionState created = routeSessionService.createSession(
                "U10001",
                RouteSessionIntent.from(sampleRequest()),
                sampleRoute("RT001", "dating")
        );

        RouteSessionState locked = routeSessionService.lockStop(created.sessionId(), created.version(), 2);
        RouteSessionState unlocked = routeSessionService.unlockStop(created.sessionId(), locked.version(), 2);

        assertThat(locked.lockedStopOrders()).containsExactly(2);
        assertThat(locked.version()).isEqualTo(created.version() + 1);
        assertThat(unlocked.lockedStopOrders()).isEmpty();
        assertThat(unlocked.version()).isEqualTo(locked.version() + 1);
    }

    @Test
    void pendingClarificationCanBeSetAndCleared() {
        RouteSessionState created = routeSessionService.createSession(
                "U10001",
                RouteSessionIntent.from(sampleRequest()),
                sampleRoute("RT001", "dating")
        );
        PendingClarification clarification = new PendingClarification(
                "Do you mean stop 1 or stop 3?",
                List.of("target_stop"),
                List.of("1|dinner|Dinner A", "3|bar|Bar C"),
                "That one is too expensive, replace it.",
                LocalDateTime.of(2026, 5, 28, 20, 0)
        );

        RouteSessionState waiting = routeSessionService.setPendingClarification(created.sessionId(), created.version(), clarification);
        RouteSessionState cleared = routeSessionService.clearPendingClarification(created.sessionId(), waiting.version());

        assertThat(waiting.status()).isEqualTo(RouteSessionStatus.WAITING_CLARIFICATION);
        assertThat(waiting.pendingClarification()).isEqualTo(clarification);
        assertThat(cleared.status()).isEqualTo(RouteSessionStatus.ACTIVE);
        assertThat(cleared.pendingClarification()).isNull();
    }

    @Test
    void changeHistoryAppendsCorrectly() {
        RouteSessionState created = routeSessionService.createSession(
                "U10001",
                RouteSessionIntent.from(sampleRequest()),
                sampleRoute("RT001", "dating")
        );
        RouteChangeRecord first = new RouteChangeRecord(
                "C10001",
                "REPLACE_STOP",
                "Replace dinner with a cheaper option",
                1,
                sampleRoute("RT001", "dating"),
                sampleRoute("RT020", "dating"),
                LocalDateTime.of(2026, 5, 28, 20, 10)
        );
        RouteChangeRecord second = new RouteChangeRecord(
                "C10002",
                "LOCK_STOP",
                "Keep the second stop fixed",
                2,
                sampleRoute("RT020", "dating"),
                sampleRoute("RT020", "dating"),
                LocalDateTime.of(2026, 5, 28, 20, 12)
        );

        RouteSessionState afterFirst = routeSessionService.appendChangeHistory(created.sessionId(), created.version(), first);
        RouteSessionState afterSecond = routeSessionService.appendChangeHistory(created.sessionId(), afterFirst.version(), second);

        assertThat(afterFirst.changeHistory()).containsExactly(first);
        assertThat(afterSecond.changeHistory()).containsExactly(first, second);
        assertThat(afterSecond.version()).isEqualTo(afterFirst.version() + 1);
    }

    @Test
    void missingSessionReturnsSafeOptionalOrClearException() {
        assertThat(routeSessionService.findSession("S99999")).isEmpty();
        assertThatThrownBy(() -> routeSessionService.lockStop("S99999", 1L, 1))
                .isInstanceOf(RouteSessionNotFoundException.class)
                .hasMessageContaining("S99999");
    }

    private RoutePlanRequest sampleRequest() {
        return new RoutePlanRequest(
                "U10001",
                "dating",
                "sanlitun",
                null,
                "18:00-22:00",
                500,
                2,
                "relaxed",
                List.of("photo"),
                List.of("queue")
        );
    }

    private GeneratedRoutePlan sampleRoute(String templateId, String scene) {
        GeneratedRouteStop stop = new GeneratedRouteStop(
                1,
                "dinner",
                "P00001",
                "Dinner A",
                "sanlitun",
                "chaoyang",
                116.4567,
                39.9345,
                "GCJ-02",
                "food",
                "indoor",
                "18:00",
                "19:30",
                90,
                0.0,
                0.0,
                260,
                95.0,
                List.of("photo"),
                List.of()
        );
        return new GeneratedRoutePlan(
                templateId,
                scene,
                "18:00-22:00",
                260,
                90,
                0.0,
                120.0,
                "18:00",
                "19:30",
                List.of(stop),
                null
        );
    }
}
