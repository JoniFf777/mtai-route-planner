package com.mtai.mtairouteplanner.event;

public class NoopRouteEventPublisher implements RouteEventPublisher {

    @Override
    public void publish(RouteLifecycleEvent event) {
        // Intentionally no-op for local default mode.
    }
}
