package com.mtai.mtairouteplanner.service;

import com.mtai.mtairouteplanner.model.RouteSessionState;

import java.util.Optional;
import java.util.function.UnaryOperator;

public interface RouteSessionStore {

    boolean saveIfAbsent(RouteSessionState routeSessionState);

    Optional<RouteSessionState> findBySessionId(String sessionId);

    RouteSessionState update(String sessionId, long expectedVersion, UnaryOperator<RouteSessionState> updater);
}
