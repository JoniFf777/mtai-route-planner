package com.mtai.mtairouteplanner.config.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mtai.mtairouteplanner.event.publisher.NoopRouteEventPublisher;
import com.mtai.mtairouteplanner.event.publisher.RocketMqRouteEventPublisher;
import com.mtai.mtairouteplanner.event.publisher.RouteEventPublisher;
import com.mtai.mtairouteplanner.event.service.RouteLifecycleEventService;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class RouteEventConfig {

    private static final Logger log = LoggerFactory.getLogger(RouteEventConfig.class);

    @Bean
    public NoopRouteEventPublisher noopRouteEventPublisher() {
        return new NoopRouteEventPublisher();
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "route.events.publisher", havingValue = "noop", matchIfMissing = true)
    public RouteEventPublisher noopModeRouteEventPublisher(NoopRouteEventPublisher noopRouteEventPublisher) {
        return noopRouteEventPublisher;
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "route.events.publisher", havingValue = "rocketmq")
    public RouteEventPublisher rocketMqRouteEventPublisher(
            ObjectProvider<RocketMQTemplate> rocketMQTemplateProvider,
            ObjectMapper objectMapper,
            NoopRouteEventPublisher noopRouteEventPublisher,
            @Value("${route.events.topic:mtai-route-events}") String topic
    ) {
        RocketMQTemplate rocketMQTemplate = rocketMQTemplateProvider.getIfAvailable();
        if (rocketMQTemplate == null) {
            log.warn("route.events.publisher=rocketmq but RocketMQTemplate is unavailable. Falling back to NoopRouteEventPublisher.");
            return noopRouteEventPublisher;
        }
        return new RocketMqRouteEventPublisher(rocketMQTemplate, objectMapper, topic);
    }

    @Bean
    public RouteLifecycleEventService routeLifecycleEventService(RouteEventPublisher routeEventPublisher) {
        return new RouteLifecycleEventService(routeEventPublisher);
    }
}

