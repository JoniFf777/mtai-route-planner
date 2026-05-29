package com.mtai.mtairouteplanner.config;

import com.mtai.mtairouteplanner.ai.FakeIntentAgentService;
import com.mtai.mtairouteplanner.ai.FakePresenterService;
import com.mtai.mtairouteplanner.service.ClarificationService;
import com.mtai.mtairouteplanner.service.RouteAdjustmentService;
import com.mtai.mtairouteplanner.service.RouteContextAssembler;
import com.mtai.mtairouteplanner.service.RouteOptimizerService;
import com.mtai.mtairouteplanner.service.RouteSessionService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
    public RouteSessionService routeSessionService() {
        return new RouteSessionService();
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
