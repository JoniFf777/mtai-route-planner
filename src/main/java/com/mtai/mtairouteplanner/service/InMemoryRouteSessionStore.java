package com.mtai.mtairouteplanner.service;

import com.mtai.mtairouteplanner.model.RouteSessionState;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.UnaryOperator;

public class InMemoryRouteSessionStore implements RouteSessionStore {

    private final ConcurrentMap<String, RouteSessionState> sessions = new ConcurrentHashMap<>();

    @Override
    public boolean saveIfAbsent(RouteSessionState routeSessionState) {
        return sessions.putIfAbsent(routeSessionState.sessionId(), routeSessionState) == null;
    }

    @Override
    public Optional<RouteSessionState> findBySessionId(String sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    @Override
    public RouteSessionState update(String sessionId, long expectedVersion, UnaryOperator<RouteSessionState> updater) {
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
}
