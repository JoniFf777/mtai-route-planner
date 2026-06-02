package com.mtai.mtairouteplanner.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mtai.mtairouteplanner.ai.intent.IntentAgentService;
import com.mtai.mtairouteplanner.model.route.GeneratedRoutePlan;
import com.mtai.mtairouteplanner.model.route.GeneratedRouteStop;
import com.mtai.mtairouteplanner.model.route.RoutePlanRequest;
import com.mtai.mtairouteplanner.service.route.planning.RouteOptimizerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

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
class RoutePlanningControllerIntentAgentAbstractionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private IntentAgentService intentAgentService;

    @MockBean
    private RouteOptimizerService routeOptimizerService;

    @Test
    void controllerUsesIntentAgentServiceAbstractionForPlanning() throws Exception {
        RoutePlanRequest routePlanRequest = new RoutePlanRequest(
                "U10001",
                "Citywalk",
                null,
                "chaoyang",
                "13:00-22:00",
                800,
                2,
                "relaxed",
                List.of("photo"),
                List.of()
        );
        given(intentAgentService.parsePlanRequestResult(eq("U10001"), eq("test message")))
                .willReturn(new IntentAgentService.PlanParseResult(routePlanRequest, routePlanRequest, "single-parser"));
        given(routeOptimizerService.generateRoutes(eq(routePlanRequest)))
                .willReturn(List.of(sampleRoute("T10001", "Citywalk Spot A")));

        mockMvc.perform(post("/api/routes/plan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "user_id", "U10001",
                                "message", "test message"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.session.current_intent.scene").value("Citywalk"));

        then(intentAgentService).should().parsePlanRequestResult("U10001", "test message");
        then(routeOptimizerService).should().generateRoutes(routePlanRequest);
    }

    @Test
    void controllerRetriesWithFallbackParsedRequestWhenPrimaryYieldsNoRoute() throws Exception {
        RoutePlanRequest strictSpringAiRequest = new RoutePlanRequest(
                "U10001",
                "dating",
                "sanlitun",
                "chaoyang",
                "19:00-21:00",
                500,
                2,
                "tight",
                List.of("photo", "romantic"),
                List.of("expensive")
        );
        RoutePlanRequest fakeFallbackRequest = new RoutePlanRequest(
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
        given(intentAgentService.parsePlanRequestResult(eq("U10001"), eq("date message")))
                .willReturn(new IntentAgentService.PlanParseResult(
                        strictSpringAiRequest,
                        fakeFallbackRequest,
                        "fake={...} | spring_ai={...}"
                ));
        given(routeOptimizerService.generateRoutes(eq(strictSpringAiRequest))).willReturn(List.of());
        given(routeOptimizerService.generateRoutes(eq(fakeFallbackRequest)))
                .willReturn(List.of(sampleRoute("T20001", "Dinner A")));

        mockMvc.perform(post("/api/routes/plan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "user_id", "U10001",
                                "message", "date message"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.session.current_intent.district").value("chaoyang"))
                .andExpect(jsonPath("$.session.current_intent.time_window").value("18:00-22:00"));

        then(intentAgentService).should().parsePlanRequestResult("U10001", "date message");
        then(routeOptimizerService).should().generateRoutes(strictSpringAiRequest);
        then(routeOptimizerService).should().generateRoutes(fakeFallbackRequest);
    }

    private GeneratedRoutePlan sampleRoute(String templateId, String poiName) {
        return new GeneratedRoutePlan(
                templateId,
                "Citywalk",
                "13:00-22:00",
                220,
                180,
                2.4,
                88.5,
                "13:00",
                "16:00",
                List.of(new GeneratedRouteStop(
                        1,
                        "sightseeing",
                        "P10001",
                        poiName,
                        "sanlitun",
                        "chaoyang",
                        116.4567,
                        39.9345,
                        "GCJ-02",
                        "sightseeing",
                        "outdoor",
                        "13:00",
                        "14:00",
                        60,
                        0.0,
                        0.0,
                        0,
                        42.0,
                        List.of("photo"),
                        List.of()
                )),
                null
        );
    }
}

