package com.mtai.mtairouteplanner.service;

public class RouteSessionVersionConflictException extends RuntimeException {

    public RouteSessionVersionConflictException(String sessionId, long expectedVersion, long actualVersion) {
        super("Route session version conflict for " + sessionId
                + ": expected " + expectedVersion
                + " but was " + actualVersion);
    }
}
