package com.mtai.mtairouteplanner.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mtai.mtairouteplanner.ai.FakeIntentAgentService;
import com.mtai.mtairouteplanner.ai.FakePresenterService;
import com.mtai.mtairouteplanner.service.ClarificationService;
import com.mtai.mtairouteplanner.service.InMemoryRouteSessionStore;
import com.mtai.mtairouteplanner.service.RedisRouteSessionStore;
import com.mtai.mtairouteplanner.service.RouteAdjustmentService;
import com.mtai.mtairouteplanner.service.RouteContextAssembler;
import com.mtai.mtairouteplanner.service.RouteOptimizerService;
import com.mtai.mtairouteplanner.service.RouteSessionService;
import com.mtai.mtairouteplanner.service.RouteSessionStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class DevRouteApiConfig {

    @Bean
    public FakeIntentAgentService fakeIntentAgentService() {
        return new FakeIntentAgentService();
    }

    @Bean
    public FakePresenterService fakePresenterService() {
        return new FakePresenterService();
    }

    @Bean
    @ConditionalOnProperty(name = "route.session.store", havingValue = "memory", matchIfMissing = true)
    public RouteSessionStore inMemoryRouteSessionStore() {
        return new InMemoryRouteSessionStore();
    }

    @Bean
    @ConditionalOnProperty(name = "route.session.store", havingValue = "redis")
    public RouteSessionStore redisRouteSessionStore(StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper) {
        return new RedisRouteSessionStore(stringRedisTemplate, objectMapper);
    }

    @Bean
    public RouteSessionService routeSessionService(RouteSessionStore routeSessionStore) {
        return new RouteSessionService(routeSessionStore);
    }

    @Bean
    public RouteOptimizerService routeOptimizerService() {
        return new RouteOptimizerService();
    }

    @Bean
    public RouteAdjustmentService routeAdjustmentService(RouteSessionService routeSessionService) {
        return new RouteAdjustmentService(routeSessionService);
    }

    @Bean
    public RouteContextAssembler routeContextAssembler() {
        return new RouteContextAssembler();
    }

    @Bean
    public ClarificationService clarificationService(RouteSessionService routeSessionService) {
        return new ClarificationService(routeSessionService);
    }
}
