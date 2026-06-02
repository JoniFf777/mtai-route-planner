package com.mtai.mtairouteplanner.service.route.session;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mtai.mtairouteplanner.model.session.RouteSessionState;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

public class RedisRouteSessionStore implements RouteSessionStore {

    private static final String KEY_PREFIX = "route:session:";
    private static final int MAX_UPDATE_RETRIES = 5;

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public RedisRouteSessionStore(StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean saveIfAbsent(RouteSessionState routeSessionState) {
        return Boolean.TRUE.equals(stringRedisTemplate.opsForValue()
                .setIfAbsent(sessionKey(routeSessionState.sessionId()), serialize(routeSessionState)));
    }

    @Override
    public Optional<RouteSessionState> findBySessionId(String sessionId) {
        String value = stringRedisTemplate.opsForValue().get(sessionKey(sessionId));
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(deserialize(sessionId, value));
    }

    @Override
    public RouteSessionState update(String sessionId, long expectedVersion, UnaryOperator<RouteSessionState> updater) {
        String key = sessionKey(sessionId);
        for (int attempt = 0; attempt < MAX_UPDATE_RETRIES; attempt++) {
            RouteSessionState updated = stringRedisTemplate.execute(new SessionCallback<>() {
                @Override
                @SuppressWarnings({"rawtypes", "unchecked"})
                public RouteSessionState execute(RedisOperations operations) {
                    operations.watch(key);
                    String currentJson = (String) operations.opsForValue().get(key);
                    if (currentJson == null) {
                        operations.unwatch();
                        throw new RouteSessionNotFoundException(sessionId);
                    }

                    RouteSessionState current = deserialize(sessionId, currentJson);
                    if (current.version() != expectedVersion) {
                        operations.unwatch();
                        throw new RouteSessionVersionConflictException(sessionId, expectedVersion, current.version());
                    }

                    RouteSessionState next = updater.apply(current);
                    operations.multi();
                    operations.opsForValue().set(key, serialize(next));
                    List<Object> execResult = operations.exec();
                    return execResult == null ? null : next;
                }
            });
            if (updated != null) {
                return updated;
            }
        }

        RouteSessionState latest = findBySessionId(sessionId)
                .orElseThrow(() -> new RouteSessionNotFoundException(sessionId));
        throw new RouteSessionVersionConflictException(sessionId, expectedVersion, latest.version());
    }

    private String sessionKey(String sessionId) {
        return KEY_PREFIX + sessionId;
    }

    private String serialize(RouteSessionState routeSessionState) {
        try {
            return objectMapper.writeValueAsString(routeSessionState);
        } catch (JsonProcessingException exception) {
            throw new UncheckedIOException("Failed to serialize route session '" + routeSessionState.sessionId() + "'.", exception);
        }
    }

    private RouteSessionState deserialize(String sessionId, String serializedValue) {
        try {
            return objectMapper.readValue(serializedValue, RouteSessionState.class);
        } catch (JsonProcessingException exception) {
            throw new UncheckedIOException("Failed to deserialize route session '" + sessionId + "'.", exception);
        }
    }
}


