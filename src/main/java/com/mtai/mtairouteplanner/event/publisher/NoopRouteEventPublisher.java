package com.mtai.mtairouteplanner.event.publisher;

import com.mtai.mtairouteplanner.event.model.RouteLifecycleEvent;

public class NoopRouteEventPublisher implements RouteEventPublisher {

    @Override
    public void publish(RouteLifecycleEvent event) {
        // Intentionally no-op for local default mode.
    }
}


