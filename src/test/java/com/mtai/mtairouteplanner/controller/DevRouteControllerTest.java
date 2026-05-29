package com.mtai.mtairouteplanner.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "route.session.store=memory")
@AutoConfigureMockMvc
class DevRouteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void postPlanStructuredReturnsSessionIdAndRoute() throws Exception {
        mockMvc.perform(post("/api/dev/routes/plan-structured")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "user_id": "U10003",
                                  "scene": "Citywalk",
                                  "district": "东城区",
                                  "time_window": "13:00-22:00",
                                  "budget_total": 800,
                                  "party_size": 2,
                                  "pace": "适中",
                                  "prefer_tags": ["适合拍照"],
                                  "avoid_tags": []
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.session_id").isNotEmpty())
                .andExpect(jsonPath("$.route.template_id").isNotEmpty())
                .andExpect(jsonPath("$.route.stops").isArray());
    }

    @Test
    void getSessionReturnsCurrentSession() throws Exception {
        String sessionId = planSession("""
                {
                  "user_id": "U10004",
                  "scene": "雨天路线",
                  "district": "朝阳区",
                  "time_window": "13:00-21:00",
                  "budget_total": 800,
                  "party_size": 2,
                  "pace": "轻松",
                  "prefer_tags": ["展览"],
                  "avoid_tags": []
                }
                """);

        mockMvc.perform(get("/api/dev/routes/{sessionId}", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.session_id").value(sessionId))
                .andExpect(jsonPath("$.current_route.template_id").isNotEmpty())
                .andExpect(jsonPath("$.current_intent.scene").value("雨天路线"));
    }

    @Test
    void adjustStructuredCanLockAStop() throws Exception {
        String sessionId = planSession("""
                {
                  "user_id": "U10003",
                  "scene": "Citywalk",
                  "district": "东城区",
                  "time_window": "13:00-22:00",
                  "budget_total": 800,
                  "party_size": 2,
                  "pace": "适中",
                  "prefer_tags": ["适合拍照"],
                  "avoid_tags": []
                }
                """);

        mockMvc.perform(post("/api/dev/routes/{sessionId}/adjust-structured", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "change_type": "LOCK_STOP",
                                  "target_stop_order": 2
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.session_state.locked_stop_orders[0]").value(2));
    }

    @Test
    void adjustStructuredCanLowerBudget() throws Exception {
        String sessionId = planSession("""
                {
                  "user_id": "U10001",
                  "scene": "情侣约会",
                  "district": "朝阳区",
                  "time_window": "16:00-23:00",
                  "budget_total": 1200,
                  "party_size": 2,
                  "pace": "轻松",
                  "prefer_tags": ["安静"],
                  "avoid_tags": []
                }
                """);

        mockMvc.perform(post("/api/dev/routes/{sessionId}/adjust-structured", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "change_type": "LOWER_BUDGET",
                                  "new_budget_total": 650,
                                  "avoid_tags": ["太贵"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.session_state.current_intent.budget_total").value(650))
                .andExpect(jsonPath("$.adjusted_route.total_budget").isNumber());
    }

    @Test
    void getContextReturnsCompactSessionContext() throws Exception {
        String sessionId = planSession("""
                {
                  "user_id": "U10003",
                  "scene": "Citywalk",
                  "district": "东城区",
                  "time_window": "13:00-22:00",
                  "budget_total": 800,
                  "party_size": 2,
                  "pace": "适中",
                  "prefer_tags": ["适合拍照"],
                  "avoid_tags": []
                }
                """);

        mockMvc.perform(post("/api/dev/routes/{sessionId}/adjust-structured", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "change_type": "LOCK_STOP",
                                  "target_stop_order": 2
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/dev/routes/{sessionId}/context", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.session_id").value(sessionId))
                .andExpect(jsonPath("$.locked_stop_orders[0]").value(2))
                .andExpect(jsonPath("$.current_route_stops[0].poi_id").isNotEmpty())
                .andExpect(jsonPath("$.current_route_stops[0].matched_prefer_tags").doesNotExist());
    }

    @Test
    void clarificationAnswerResolvesPendingClarification() throws Exception {
        String sessionId = planSession("""
                {
                  "user_id": "U10003",
                  "scene": "Citywalk",
                  "district": "东城区",
                  "time_window": "13:00-22:00",
                  "budget_total": 800,
                  "party_size": 2,
                  "pace": "适中",
                  "prefer_tags": ["适合拍照"],
                  "avoid_tags": []
                }
                """);

        mockMvc.perform(post("/api/dev/routes/{sessionId}/adjust-structured", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "change_type": "REPLACE_STOP"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WAITING_CLARIFICATION"))
                .andExpect(jsonPath("$.session_state.pending_clarification.question").isNotEmpty());

        mockMvc.perform(post("/api/dev/routes/{sessionId}/clarification/answer", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "target_stop_order": 2
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.resolved_change_request.target_stop_order").value(2))
                .andExpect(jsonPath("$.session_state.pending_clarification").doesNotExist());
    }

    @Test
    void invalidSessionReturns404() throws Exception {
        mockMvc.perform(get("/api/dev/routes/{sessionId}", "S99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));
    }

    @Test
    void noFeasibleRouteReturnsSafeFailureResponse() throws Exception {
        mockMvc.perform(post("/api/dev/routes/plan-structured")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "user_id": "U10001",
                                  "scene": "情侣约会",
                                  "business_area": "三里屯",
                                  "district": "朝阳区",
                                  "time_window": "18:00-19:00",
                                  "budget_total": 60,
                                  "party_size": 2,
                                  "pace": "轻松",
                                  "prefer_tags": [],
                                  "avoid_tags": ["排队久", "夜景"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.session_id").doesNotExist())
                .andExpect(jsonPath("$.route").doesNotExist());
    }

    private String planSession(String requestJson) throws Exception {
        MvcResult mvcResult = mockMvc.perform(post("/api/dev/routes/plan-structured")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andReturn();

        JsonNode jsonNode = objectMapper.readTree(mvcResult.getResponse().getContentAsString());
        String sessionId = jsonNode.path("session_id").asText();
        assertThat(sessionId).isNotBlank();
        return sessionId;
    }
}
