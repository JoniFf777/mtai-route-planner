package com.mtai.mtairouteplanner.service.route.session;

import com.mtai.mtairouteplanner.model.route.GeneratedRoutePlan;
import com.mtai.mtairouteplanner.model.clarification.PendingClarification;
import com.mtai.mtairouteplanner.model.adjustment.RouteChangeRecord;
import com.mtai.mtairouteplanner.model.session.RouteSessionIntent;
import com.mtai.mtairouteplanner.model.session.RouteSessionState;
import com.mtai.mtairouteplanner.model.session.RouteSessionStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public class RouteSessionService {

    private final RouteSessionStore routeSessionStore;
    private final AtomicLong sessionSequence = new AtomicLong(10000);

    public RouteSessionService() {
        this(new InMemoryRouteSessionStore());
    }

    public RouteSessionService(RouteSessionStore routeSessionStore) {
        this.routeSessionStore = Objects.requireNonNull(routeSessionStore, "routeSessionStore must not be null");
    }

    public RouteSessionState createSession(String userId, RouteSessionIntent intent, GeneratedRoutePlan route) {
        while (true) {
            String sessionId = "S" + sessionSequence.incrementAndGet();
            RouteSessionState routeSessionState = new RouteSessionState(
                    sessionId,
                    userId,
                    RouteSessionStatus.ACTIVE,
                    intent,
                    route,
                    Set.of(),
                    null,
                    List.of(),
                    1L,
                    LocalDateTime.now()
            );
            if (routeSessionStore.saveIfAbsent(routeSessionState)) {
                return routeSessionState;
            }
        }
    }

    public Optional<RouteSessionState> findSession(String sessionId) {
        return routeSessionStore.findBySessionId(sessionId);
    }

    public RouteSessionState updateCurrentRoute(String sessionId, long expectedVersion, GeneratedRoutePlan newRoute) {
        return routeSessionStore.update(sessionId, expectedVersion, current -> new RouteSessionState(
                current.sessionId(),
                current.userId(),
                current.status(),
                current.currentIntent(),
                newRoute,
                current.lockedStopOrders(),
                current.pendingClarification(),
                current.changeHistory(),
                current.version() + 1,
                LocalDateTime.now()
        ));
    }

    public RouteSessionState updateCurrentIntent(String sessionId, long expectedVersion, RouteSessionIntent newIntent) {
        return routeSessionStore.update(sessionId, expectedVersion, current -> new RouteSessionState(
                current.sessionId(),
                current.userId(),
                current.status(),
                newIntent,
                current.currentRoute(),
                current.lockedStopOrders(),
                current.pendingClarification(),
                current.changeHistory(),
                current.version() + 1,
                LocalDateTime.now()
        ));
    }

    public RouteSessionState lockStop(String sessionId, long expectedVersion, int stopOrder) {
        return routeSessionStore.update(sessionId, expectedVersion, current -> {
            Set<Integer> lockedStopOrders = new LinkedHashSet<>(current.lockedStopOrders());
            lockedStopOrders.add(stopOrder);
            return new RouteSessionState(
                    current.sessionId(),
                    current.userId(),
                    current.status(),
                    current.currentIntent(),
                    current.currentRoute(),
                    lockedStopOrders,
                    current.pendingClarification(),
                    current.changeHistory(),
                    current.version() + 1,
                    LocalDateTime.now()
            );
        });
    }

    public RouteSessionState unlockStop(String sessionId, long expectedVersion, int stopOrder) {
        return routeSessionStore.update(sessionId, expectedVersion, current -> {
            Set<Integer> lockedStopOrders = new LinkedHashSet<>(current.lockedStopOrders());
            lockedStopOrders.remove(stopOrder);
            return new RouteSessionState(
                    current.sessionId(),
                    current.userId(),
                    current.status(),
                    current.currentIntent(),
                    current.currentRoute(),
                    lockedStopOrders,
                    current.pendingClarification(),
                    current.changeHistory(),
                    current.version() + 1,
                    LocalDateTime.now()
            );
        });
    }

    public RouteSessionState setPendingClarification(String sessionId, long expectedVersion, PendingClarification clarification) {
        return routeSessionStore.update(sessionId, expectedVersion, current -> new RouteSessionState(
                current.sessionId(),
                current.userId(),
                RouteSessionStatus.WAITING_CLARIFICATION,
                current.currentIntent(),
                current.currentRoute(),
                current.lockedStopOrders(),
                clarification,
                current.changeHistory(),
                current.version() + 1,
                LocalDateTime.now()
        ));
    }

    public RouteSessionState clearPendingClarification(String sessionId, long expectedVersion) {
        return routeSessionStore.update(sessionId, expectedVersion, current -> new RouteSessionState(
                current.sessionId(),
                current.userId(),
                current.status() == RouteSessionStatus.WAITING_CLARIFICATION ? RouteSessionStatus.ACTIVE : current.status(),
                current.currentIntent(),
                current.currentRoute(),
                current.lockedStopOrders(),
                null,
                current.changeHistory(),
                current.version() + 1,
                LocalDateTime.now()
        ));
    }

    public RouteSessionState appendChangeHistory(String sessionId, long expectedVersion, RouteChangeRecord changeRecord) {
        return routeSessionStore.update(sessionId, expectedVersion, current -> {
            List<RouteChangeRecord> changeHistory = new ArrayList<>(current.changeHistory());
            changeHistory.add(changeRecord);
            return new RouteSessionState(
                    current.sessionId(),
                    current.userId(),
                    current.status(),
                    current.currentIntent(),
                    current.currentRoute(),
                    current.lockedStopOrders(),
                    current.pendingClarification(),
                    changeHistory,
                    current.version() + 1,
                    LocalDateTime.now()
            );
        });
    }
}


