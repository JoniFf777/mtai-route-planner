package com.mtai.mtairouteplanner.service;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.mtai.mtairouteplanner.model.ChangeRequest;
import com.mtai.mtairouteplanner.model.ChangeType;
import com.mtai.mtairouteplanner.model.GeneratedRoutePlan;
import com.mtai.mtairouteplanner.model.GeneratedRouteStop;
import com.mtai.mtairouteplanner.model.PendingClarification;
import com.mtai.mtairouteplanner.model.RouteChangeRecord;
import com.mtai.mtairouteplanner.model.RoutePlanRequest;
import com.mtai.mtairouteplanner.model.RouteSessionIntent;
import com.mtai.mtairouteplanner.model.RouteSessionState;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnabledIfSystemProperty(named = "route.redis.tests", matches = "true")
class RedisRouteSessionStoreIntegrationTest {

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate stringRedisTemplate;

    private RouteSessionStore routeSessionStore;
    private RouteSessionService routeSessionService;

    @BeforeAll
    static void setUpRedisClient() {
        connectionFactory = new LettuceConnectionFactory("localhost", 6379);
        connectionFactory.afterPropertiesSet();

        stringRedisTemplate = new StringRedisTemplate();
        stringRedisTemplate.setConnectionFactory(connectionFactory);
        stringRedisTemplate.afterPropertiesSet();
    }

    @AfterAll
    static void tearDownRedisClient() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @BeforeEach
    void setUp() {
        clearSessionKeys();
        routeSessionStore = new RedisRouteSessionStore(
                stringRedisTemplate,
                JsonMapper.builder().findAndAddModules().build()
        );
        routeSessionService = new RouteSessionService(routeSessionStore);
    }

    @Test
    void redisStoreCanSaveAndReadRouteSessionState() {
        RouteSessionState sessionState = new RouteSessionState(
                "S20001",
                "U10001",
                null,
                RouteSessionIntent.from(sampleRequest()),
                sampleRoute("RT001"),
                Set.of(2),
                sampleClarification(),
                List.of(sampleChangeRecord(sampleRoute("RT001"))),
                3L,
                LocalDateTime.of(2026, 5, 29, 10, 0)
        );

        assertThat(routeSessionStore.saveIfAbsent(sessionState)).isTrue();

        RouteSessionState reloaded = routeSessionStore.findBySessionId(sessionState.sessionId()).orElseThrow();
        assertThat(reloaded).isEqualTo(sessionState);
        assertThat(reloaded.lockedStopOrders()).containsExactly(2);
        assertThat(reloaded.pendingClarification()).isEqualTo(sampleClarification());
        assertThat(reloaded.changeHistory()).containsExactly(sampleChangeRecord(sampleRoute("RT001")));
    }

    @Test
    void redisStorePreservesVersionIncrementsAndRejectsConflicts() {
        RouteSessionState created = routeSessionService.createSession(
                "U10002",
                RouteSessionIntent.from(sampleRequest()),
                sampleRoute("RT001")
        );

        RouteSessionState locked = routeSessionService.lockStop(created.sessionId(), created.version(), 2);
        RouteSessionState waiting = routeSessionService.setPendingClarification(
                locked.sessionId(),
                locked.version(),
                sampleClarification()
        );
        RouteSessionState updated = routeSessionService.appendChangeHistory(
                waiting.sessionId(),
                waiting.version(),
                sampleChangeRecord(waiting.currentRoute())
        );

        assertThat(updated.version()).isEqualTo(created.version() + 3);

        RouteSessionState reloaded = routeSessionService.findSession(created.sessionId()).orElseThrow();
        assertThat(reloaded.lockedStopOrders()).containsExactly(2);
        assertThat(reloaded.pendingClarification()).isEqualTo(sampleClarification());
        assertThat(reloaded.changeHistory()).containsExactly(sampleChangeRecord(waiting.currentRoute()));

        assertThatThrownBy(() -> routeSessionService.updateCurrentRoute(
                created.sessionId(),
                created.version(),
                sampleRoute("RT002")
        )).isInstanceOf(RouteSessionVersionConflictException.class);
    }

    private void clearSessionKeys() {
        Set<String> keys = stringRedisTemplate.keys("route:session:*");
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }

    private RoutePlanRequest sampleRequest() {
        return new RoutePlanRequest(
                "U10001",
                "dating",
                "sanlitun",
                "chaoyang",
                "18:00-22:00",
                500,
                2,
                "relaxed",
                List.of("photo"),
                List.of("queue")
        );
    }

    private PendingClarification sampleClarification() {
        return new PendingClarification(
                "Which stop should be changed?",
                List.of("target_stop_order"),
                List.of("1|dinner|Dinner A", "2|walk|Walk A"),
                "replace it",
                LocalDateTime.of(2026, 5, 29, 10, 5),
                new ChangeRequest(ChangeType.REPLACE_STOP, null, null, null, null, List.of(), List.of(), List.of())
        );
    }

    private RouteChangeRecord sampleChangeRecord(GeneratedRoutePlan route) {
        return new RouteChangeRecord(
                "C10001",
                ChangeType.LOCK_STOP.name(),
                "lock second stop",
                2,
                route,
                route,
                LocalDateTime.of(2026, 5, 29, 10, 10)
        );
    }

    private GeneratedRoutePlan sampleRoute(String templateId) {
        return new GeneratedRoutePlan(
                templateId,
                "dating",
                "18:00-22:00",
                260,
                180,
                2.5,
                120.0,
                "18:00",
                "21:00",
                List.of(
                        new GeneratedRouteStop(
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
                                "19:00",
                                60,
                                0.0,
                                0.0,
                                160,
                                90.0,
                                List.of("photo"),
                                List.of()
                        ),
                        new GeneratedRouteStop(
                                2,
                                "walk",
                                "P00002",
                                "Walk A",
                                "sanlitun",
                                "chaoyang",
                                116.4599,
                                39.9368,
                                "GCJ-02",
                                "sight",
                                "outdoor",
                                "19:20",
                                "20:20",
                                60,
                                1.2,
                                20.0,
                                0,
                                85.0,
                                List.of("photo"),
                                List.of()
                        )
                ),
                null
        );
    }
}
