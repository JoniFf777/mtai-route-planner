package com.mtai.mtairouteplanner.service.route.session;

import com.mtai.mtairouteplanner.model.session.RouteSessionState;

import java.util.Optional;
import java.util.function.UnaryOperator;

public interface RouteSessionStore {

    boolean saveIfAbsent(RouteSessionState routeSessionState);

    Optional<RouteSessionState> findBySessionId(String sessionId);

    RouteSessionState update(String sessionId, long expectedVersion, UnaryOperator<RouteSessionState> updater);
}


