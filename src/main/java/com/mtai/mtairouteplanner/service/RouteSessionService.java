package com.mtai.mtairouteplanner.service;

import com.mtai.mtairouteplanner.model.GeneratedRoutePlan;
import com.mtai.mtairouteplanner.model.PendingClarification;
import com.mtai.mtairouteplanner.model.RouteChangeRecord;
import com.mtai.mtairouteplanner.model.RouteSessionIntent;
import com.mtai.mtairouteplanner.model.RouteSessionState;
import com.mtai.mtairouteplanner.model.RouteSessionStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.UnaryOperator;

public class RouteSessionService {

    private final ConcurrentMap<String, RouteSessionState> sessions = new ConcurrentHashMap<>();
    private final AtomicLong sessionSequence = new AtomicLong(10000);

    public RouteSessionState createSession(String userId, RouteSessionIntent intent, GeneratedRoutePlan route) {
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
        sessions.put(sessionId, routeSessionState);
        return routeSessionState;
    }

    public Optional<RouteSessionState> findSession(String sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    public RouteSessionState updateCurrentRoute(String sessionId, long expectedVersion, GeneratedRoutePlan newRoute) {
        return updateWithVersionCheck(sessionId, expectedVersion, current -> new RouteSessionState(
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
        return updateWithVersionCheck(sessionId, expectedVersion, current -> new RouteSessionState(
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

    public RouteSessionState lockStop(String sessionId, int stopOrder) {
        return updateWithoutVersionCheck(sessionId, current -> {
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

    public RouteSessionState unlockStop(String sessionId, int stopOrder) {
        return updateWithoutVersionCheck(sessionId, current -> {
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

    public RouteSessionState setPendingClarification(String sessionId, PendingClarification clarification) {
        return updateWithoutVersionCheck(sessionId, current -> new RouteSessionState(
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

    public RouteSessionState clearPendingClarification(String sessionId) {
        return updateWithoutVersionCheck(sessionId, current -> new RouteSessionState(
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

    public RouteSessionState appendChangeHistory(String sessionId, RouteChangeRecord changeRecord) {
        return updateWithoutVersionCheck(sessionId, current -> {
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

    private RouteSessionState updateWithVersionCheck(
            String sessionId,
            long expectedVersion,
            UnaryOperator<RouteSessionState> updater
    ) {
        RouteSessionState updated = sessions.compute(sessionId, (key, current) -> {
            if (current == null) {
                throw new RouteSessionNotFoundException(sessionId);
            }
            if (current.version() != expectedVersion) {
                throw new RouteSessionVersionConflictException(sessionId, expectedVersion, current.version());
            }
            return updater.apply(current);
        });
        return Optional.ofNullable(updated).orElseThrow(() -> new RouteSessionNotFoundException(sessionId));
    }

    private RouteSessionState updateWithoutVersionCheck(
            String sessionId,
            UnaryOperator<RouteSessionState> updater
    ) {
        RouteSessionState updated = sessions.compute(sessionId, (key, current) -> {
            if (current == null) {
                throw new RouteSessionNotFoundException(sessionId);
            }
            return updater.apply(current);
        });
        return Optional.ofNullable(updated).orElseThrow(() -> new RouteSessionNotFoundException(sessionId));
    }
}
