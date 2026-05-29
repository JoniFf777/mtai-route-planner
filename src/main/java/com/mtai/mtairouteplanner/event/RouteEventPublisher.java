package com.mtai.mtairouteplanner.event;

public interface RouteEventPublisher {

    void publish(RouteLifecycleEvent event);
}
