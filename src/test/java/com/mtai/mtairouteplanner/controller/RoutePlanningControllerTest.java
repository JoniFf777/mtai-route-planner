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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class RoutePlanningControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void postPlanParsesDatingMessage() throws Exception {
        MvcResult mvcResult = mockMvc.perform(post("/api/routes/plan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson("U10001", "今晚想和女朋友在三里屯约会，预算500，不想太累，最好能拍照。")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.session_id").isNotEmpty())
                .andExpect(jsonPath("$.route.template_id").isNotEmpty())
                .andExpect(jsonPath("$.message").value(containsString("路线概览")))
                .andExpect(jsonPath("$.message").value(containsString("预计总预算约")))
                .andExpect(jsonPath("$.session.current_intent.budget_total").value(500))
                .andExpect(jsonPath("$.session.current_intent.party_size").value(2))
                .andReturn();

        JsonNode jsonNode = objectMapper.readTree(mvcResult.getResponse().getContentAsString());
        String message = jsonNode.path("message").asText();
        JsonNode firstStop = jsonNode.path("route").path("stops").get(0);
        assertThat(message).contains("行程明细");
        assertThat(message).contains("总时长约");
        assertThat(message).contains(firstStop.path("poi_name").asText());
        assertThat(message).contains(firstStop.path("arrive_time").asText());
    }

    @Test
    void postPlanParsesCitywalkMessage() throws Exception {
        mockMvc.perform(post("/api/routes/plan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson("U10002", "周末想在南锣鼓巷 citywalk，预算200，想吃点小吃。")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.route.template_id").isNotEmpty())
                .andExpect(jsonPath("$.session.current_intent.budget_total").value(200))
                .andExpect(jsonPath("$.session.current_intent.prefer_tags[0]").isNotEmpty());
    }

    @Test
    void postPlanParsesRainyDayKidsMessage() throws Exception {
        mockMvc.perform(post("/api/routes/plan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson("U10003", "下雨天带孩子在朝阳区玩，最好室内。")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.route.template_id").isNotEmpty())
                .andExpect(jsonPath("$.session.current_intent.party_size").value(3))
                .andExpect(jsonPath("$.session.current_intent.prefer_tags").isArray());
    }

    @Test
    void postPlanParsesStudentBudgetPerPersonMessage() throws Exception {
        mockMvc.perform(post("/api/routes/plan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson("U10004", "学生党五道口朋友聚会，预算人均100。")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.session.current_intent.budget_total").value(300));
    }

    @Test
    void getSessionReturnsCurrentSession() throws Exception {
        String sessionId = planSession("U10005", "今晚想和女朋友在三里屯约会，预算500，不想太累，最好能拍照。");

        mockMvc.perform(get("/api/routes/{sessionId}", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.session_id").value(sessionId))
                .andExpect(jsonPath("$.current_intent.budget_total").value(500))
                .andExpect(jsonPath("$.current_route.stops").isArray());
    }

    @Test
    void adjustMessageCanLockSecondStop() throws Exception {
        String sessionId = planSession("U10006", "周末想在南锣鼓巷 citywalk，预算200，想吃点小吃。");

        mockMvc.perform(post("/api/routes/{sessionId}/adjust", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson("U10006", "第二站别动。")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value(containsString("已锁定指定站点")))
                .andExpect(jsonPath("$.session.locked_stop_orders[0]").value(2));
    }

    @Test
    void adjustMessageCanLowerBudget() throws Exception {
        String sessionId = planSession("U10007", "今晚想和女朋友在三里屯约会，预算500，不想太累，最好能拍照。");

        MvcResult mvcResult = mockMvc.perform(post("/api/routes/{sessionId}/adjust", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson("U10007", "预算降到300。")))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode jsonNode = objectMapper.readTree(mvcResult.getResponse().getContentAsString());
        assertThat(jsonNode.path("status").asText()).isIn("SUCCESS", "FAILED");
        assertThat(jsonNode.path("message").asText().toLowerCase()).contains("budget");
    }

    @Test
    void datingAdjustWithoutLockedStopsReturnsSpecificBudgetResult() throws Exception {
        String sessionId = planSession("U10007A", "今晚想和女朋友在三里屯约会，预算500，不想太累，最好能拍照。");

        MvcResult mvcResult = mockMvc.perform(post("/api/routes/{sessionId}/adjust", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson("U10007A", "预算降到300。")))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode jsonNode = objectMapper.readTree(mvcResult.getResponse().getContentAsString());
        assertThat(jsonNode.path("session").path("locked_stop_orders")).isEmpty();
        assertThat(jsonNode.path("message").asText()).doesNotContain("No feasible adjusted route found");
        assertThat(jsonNode.path("status").asText()).isIn("SUCCESS", "FAILED");
        if ("SUCCESS".equals(jsonNode.path("status").asText())) {
            assertThat(jsonNode.path("session").path("current_intent").path("budget_total").asInt()).isEqualTo(300);
            assertThat(jsonNode.path("route").path("total_budget").asInt()).isLessThanOrEqualTo(300);
        } else {
            assertThat(jsonNode.path("message").asText().toLowerCase()).contains("budget");
        }
    }

    @Test
    void datingAdjustWithoutLockedStopsCanSwitchToIndoor() throws Exception {
        String sessionId = planSession("U10007B", "今晚想和女朋友在三里屯约会，预算500，不想太累，最好能拍照。");

        MvcResult mvcResult = mockMvc.perform(post("/api/routes/{sessionId}/adjust", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson("U10007B", "今天下雨，改成室内。")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andReturn();

        JsonNode jsonNode = objectMapper.readTree(mvcResult.getResponse().getContentAsString());
        assertThat(jsonNode.path("session").path("locked_stop_orders")).isEmpty();
        for (JsonNode stopNode : jsonNode.path("route").path("stops")) {
            assertThat(stopNode.path("indoor_outdoor").asText()).isEqualToIgnoringCase("indoor");
        }
    }

    @Test
    void adjustMessageCanAddCoffee() throws Exception {
        String sessionId = planSession("U10008", "周末想在南锣鼓巷 citywalk，预算400，想吃点小吃。");

        MvcResult initialSession = mockMvc.perform(get("/api/routes/{sessionId}", sessionId))
                .andExpect(status().isOk())
                .andReturn();
        int initialStopCount = objectMapper.readTree(initialSession.getResponse().getContentAsString())
                .path("current_route")
                .path("stops")
                .size();

        mockMvc.perform(post("/api/routes/{sessionId}/adjust", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson("U10008", "加一个咖啡。")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.route.stops.length()").value(initialStopCount + 1));
    }

    @Test
    void ambiguousAdjustThenFirstOneResolvesClarification() throws Exception {
        String sessionId = planSession("U10009", "今晚想和女朋友在三里屯约会，预算500，不想太累，最好能拍照。");

        mockMvc.perform(post("/api/routes/{sessionId}/adjust", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson("U10009", "换掉。")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WAITING_CLARIFICATION"))
                .andExpect(jsonPath("$.message").value(containsString("我还需要确认一下")))
                .andExpect(jsonPath("$.session.pending_clarification.question").isNotEmpty());

        mockMvc.perform(post("/api/routes/{sessionId}/adjust", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson("U10009", "第一个。")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value(containsString("已替换相关站点")))
                .andExpect(jsonPath("$.session.pending_clarification").doesNotExist());
    }

    @Test
    void noFeasibleRouteResponseHasSafeExplanation() throws Exception {
        mockMvc.perform(post("/api/routes/plan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson("U10011", "今晚想和女朋友在三里屯约会，预算60，排队不要，无法接受太贵。")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.message").value(containsString("暂时没有找到符合当前条件的路线")))
                .andExpect(jsonPath("$.message").value(containsString("可以尝试放宽预算")));
    }

    @Test
    void invalidSessionReturns404() throws Exception {
        mockMvc.perform(get("/api/routes/{sessionId}", "S99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));
    }

    private String planSession(String userId, String message) throws Exception {
        MvcResult mvcResult = mockMvc.perform(post("/api/routes/plan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(userId, message)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andReturn();

        JsonNode jsonNode = objectMapper.readTree(mvcResult.getResponse().getContentAsString());
        String sessionId = jsonNode.path("session_id").asText();
        assertThat(sessionId).isNotBlank();
        return sessionId;
    }

    private String requestJson(String userId, String message) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "user_id", userId,
                "message", message
        ));
    }
}
