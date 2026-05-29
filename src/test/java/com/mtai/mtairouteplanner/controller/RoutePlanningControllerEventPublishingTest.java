package com.mtai.mtairouteplanner.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mtai.mtairouteplanner.event.RouteEventPublisher;
import com.mtai.mtairouteplanner.event.RouteEventType;
import com.mtai.mtairouteplanner.event.RouteLifecycleEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
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
class RoutePlanningControllerEventPublishingTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RouteEventPublisher routeEventPublisher;

    @Test
    void routePlanningPublishesRoutePlannedEvent() throws Exception {
        mockMvc.perform(post("/api/dev/routes/plan-structured")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(successPlanRequestJson("U10001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));

        ArgumentCaptor<RouteLifecycleEvent> eventCaptor = ArgumentCaptor.forClass(RouteLifecycleEvent.class);
        then(routeEventPublisher).should().publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().eventType()).isEqualTo(RouteEventType.ROUTE_PLANNED);
        assertThat(eventCaptor.getValue().routeSummary()).isNotNull();
    }

    @Test
    void routeAdjustmentPublishesAdjustedEvent() throws Exception {
        String sessionId = planStructuredSession();
        reset(routeEventPublisher);

        mockMvc.perform(post("/api/dev/routes/{sessionId}/adjust-structured", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "change_type": "LOCK_STOP",
                                  "target_stop_order": 2
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));

        ArgumentCaptor<RouteLifecycleEvent> eventCaptor = ArgumentCaptor.forClass(RouteLifecycleEvent.class);
        then(routeEventPublisher).should().publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().eventType()).isEqualTo(RouteEventType.ROUTE_ADJUSTED);
        assertThat(eventCaptor.getValue().changeType()).isEqualTo("LOCK_STOP");
    }

    @Test
    void publisherFailureDoesNotFailPlanningApi() throws Exception {
        doThrow(new IllegalStateException("rocketmq unavailable"))
                .when(routeEventPublisher)
                .publish(any(RouteLifecycleEvent.class));

        mockMvc.perform(post("/api/dev/routes/plan-structured")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(successPlanRequestJson("U10003")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    private String planStructuredSession() throws Exception {
        String response = mockMvc.perform(post("/api/dev/routes/plan-structured")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(successPlanRequestJson("U10002")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).path("session_id").asText();
    }

    private String successPlanRequestJson(String userId) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "user_id", userId,
                "scene", "Citywalk",
                "district", "东城区",
                "time_window", "13:00-22:00",
                "budget_total", 800,
                "party_size", 2,
                "pace", "适中",
                "prefer_tags", List.of("适合拍照"),
                "avoid_tags", List.of()
        ));
    }
}
