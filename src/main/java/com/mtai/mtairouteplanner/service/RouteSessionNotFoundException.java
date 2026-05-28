package com.mtai.mtairouteplanner.service;

public class RouteSessionNotFoundException extends RuntimeException {

    public RouteSessionNotFoundException(String sessionId) {
        super("Route session not found: " + sessionId);
    }
}
