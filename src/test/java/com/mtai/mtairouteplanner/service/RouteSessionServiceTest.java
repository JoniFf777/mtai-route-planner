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

    private final RouteSessionService routeSessionService = new RouteSessionService();

    @Test
    void createAndReadSession() {
        RouteSessionIntent intent = RouteSessionIntent.from(sampleRequest());
        GeneratedRoutePlan route = sampleRoute("RT001", "情侣约会");

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
                sampleRoute("RT001", "情侣约会")
        );

        RouteSessionState updated = routeSessionService.updateCurrentRoute(
                created.sessionId(),
                created.version(),
                sampleRoute("RT020", "情侣约会")
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
                sampleRoute("RT001", "情侣约会")
        );

        routeSessionService.updateCurrentIntent(
                created.sessionId(),
                created.version(),
                new RouteSessionIntent("Citywalk", null, "东城区", "13:00-22:00", 800, 2, "适中", List.of("适合拍照"), List.of())
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
                sampleRoute("RT001", "情侣约会")
        );

        RouteSessionState locked = routeSessionService.lockStop(created.sessionId(), 2);
        RouteSessionState unlocked = routeSessionService.unlockStop(created.sessionId(), 2);

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
                sampleRoute("RT001", "情侣约会")
        );
        PendingClarification clarification = new PendingClarification(
                "你说的是第 1 站还是第 3 站？",
                List.of("target_stop"),
                List.of("第1站", "第3站"),
                "那家太贵了，换掉。",
                LocalDateTime.of(2026, 5, 28, 20, 0)
        );

        RouteSessionState waiting = routeSessionService.setPendingClarification(created.sessionId(), clarification);
        RouteSessionState cleared = routeSessionService.clearPendingClarification(created.sessionId());

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
                sampleRoute("RT001", "情侣约会")
        );
        RouteChangeRecord first = new RouteChangeRecord(
                "C10001",
                "REPLACE_STOP",
                "晚餐换便宜点",
                1,
                sampleRoute("RT001", "情侣约会"),
                sampleRoute("RT020", "情侣约会"),
                LocalDateTime.of(2026, 5, 28, 20, 10)
        );
        RouteChangeRecord second = new RouteChangeRecord(
                "C10002",
                "LOCK_STOP",
                "第二站别动",
                2,
                sampleRoute("RT020", "情侣约会"),
                sampleRoute("RT020", "情侣约会"),
                LocalDateTime.of(2026, 5, 28, 20, 12)
        );

        RouteSessionState afterFirst = routeSessionService.appendChangeHistory(created.sessionId(), first);
        RouteSessionState afterSecond = routeSessionService.appendChangeHistory(created.sessionId(), second);

        assertThat(afterFirst.changeHistory()).containsExactly(first);
        assertThat(afterSecond.changeHistory()).containsExactly(first, second);
        assertThat(afterSecond.version()).isEqualTo(afterFirst.version() + 1);
    }

    @Test
    void missingSessionReturnsSafeOptionalOrClearException() {
        assertThat(routeSessionService.findSession("S99999")).isEmpty();
        assertThatThrownBy(() -> routeSessionService.lockStop("S99999", 1))
                .isInstanceOf(RouteSessionNotFoundException.class)
                .hasMessageContaining("S99999");
    }

    private RoutePlanRequest sampleRequest() {
        return new RoutePlanRequest(
                "U10001",
                "情侣约会",
                "三里屯",
                null,
                "18:00-22:00",
                500,
                2,
                "轻松",
                List.of("拍照"),
                List.of("排队")
        );
    }

    private GeneratedRoutePlan sampleRoute(String templateId, String scene) {
        GeneratedRouteStop stop = new GeneratedRouteStop(
                1,
                "晚餐主餐",
                "P00001",
                "餐饮A·三里屯",
                "三里屯",
                "朝阳区",
                "餐饮",
                "indoor",
                "18:00",
                "19:30",
                90,
                0.0,
                0.0,
                260,
                95.0,
                List.of("拍照"),
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
