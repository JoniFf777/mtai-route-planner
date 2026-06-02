package com.mtai.mtairouteplanner.event.publisher;

import com.mtai.mtairouteplanner.event.model.RouteLifecycleEvent;

public interface RouteEventPublisher {

    void publish(RouteLifecycleEvent event);
}


