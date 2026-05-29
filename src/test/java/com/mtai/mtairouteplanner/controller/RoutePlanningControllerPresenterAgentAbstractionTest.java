package com.mtai.mtairouteplanner.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mtai.mtairouteplanner.ai.IntentAgentService;
import com.mtai.mtairouteplanner.ai.PresenterAgentService;
import com.mtai.mtairouteplanner.model.GeneratedRoutePlan;
import com.mtai.mtairouteplanner.model.GeneratedRouteStop;
import com.mtai.mtairouteplanner.model.RoutePlanRequest;
import com.mtai.mtairouteplanner.model.RouteSessionState;
import com.mtai.mtairouteplanner.service.RouteOptimizerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "route.session.store=memory",
        "route.intent.agent=fake",
        "route.presenter.agent=fake",
        "route.events.publisher=noop",
        "spring.ai.model.chat=none"
})
@AutoConfigureMockMvc
class RoutePlanningControllerPresenterAgentAbstractionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private IntentAgentService intentAgentService;

    @MockBean
    private RouteOptimizerService routeOptimizerService;

    @MockBean
    private PresenterAgentService presenterAgentService;

    @Test
    void controllerUsesPresenterAgentServiceAndKeepsRouteObjectUnchanged() throws Exception {
        RoutePlanRequest routePlanRequest = new RoutePlanRequest(
                "U10001",
                "dating",
                null,
                "chaoyang",
                "18:00-22:00",
                500,
                2,
                "relaxed",
                List.of("photo"),
                List.of()
        );
        GeneratedRoutePlan routePlan = new GeneratedRoutePlan(
                "T30001",
                "dating",
                "18:00-22:00",
                480,
                220,
                5.4,
                91.0,
                "18:00",
                "21:40",
                List.of(new GeneratedRouteStop(
                        1,
                        "dinner",
                        "P10001",
                        "Dinner A",
                        "sanlitun",
                        "chaoyang",
                        116.4567,
                        39.9345,
                        "GCJ-02",
                        "food",
                        "indoor",
                        "18:00",
                        "19:20",
                        80,
                        0.0,
                        0.0,
                        260,
                        45.0,
                        List.of("photo"),
                        List.of()
                )),
                null
        );

        given(intentAgentService.parsePlanRequestResult(eq("U10001"), eq("test message")))
                .willReturn(new IntentAgentService.PlanParseResult(routePlanRequest, routePlanRequest, "single-parser"));
        given(routeOptimizerService.generateRoutes(eq(routePlanRequest))).willReturn(List.of(routePlan));
        given(presenterAgentService.presentInitialRoute(any(RouteSessionState.class))).willReturn("这是一段 AI 讲解。");

        mockMvc.perform(post("/api/routes/plan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "user_id", "U10001",
                                "message", "test message"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("这是一段 AI 讲解。"))
                .andExpect(jsonPath("$.route.template_id").value("T30001"))
                .andExpect(jsonPath("$.route.stops[0].poi_name").value("Dinner A"));

        then(presenterAgentService).should().presentInitialRoute(any(RouteSessionState.class));
    }
}
