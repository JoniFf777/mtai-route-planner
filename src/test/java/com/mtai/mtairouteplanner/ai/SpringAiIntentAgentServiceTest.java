package com.mtai.mtairouteplanner.ai;

import com.mtai.mtairouteplanner.model.ChangeRequest;
import com.mtai.mtairouteplanner.model.ChangeType;
import com.mtai.mtairouteplanner.model.ClarificationAnswer;
import com.mtai.mtairouteplanner.model.CompactChangeHistoryItem;
import com.mtai.mtairouteplanner.model.CompactRouteContext;
import com.mtai.mtairouteplanner.model.CompactRouteStop;
import com.mtai.mtairouteplanner.model.PendingClarification;
import com.mtai.mtairouteplanner.model.RoutePlanRequest;
import com.mtai.mtairouteplanner.model.RouteSessionIntent;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SpringAiIntentAgentServiceTest {

    @Test
    void validPlanOutputIsConvertedToRoutePlanRequest() {
        CapturingGateway gateway = new CapturingGateway(new SpringAiIntentAgentService.PlanIntentLlmResponse(
                "Citywalk",
                "sanlitun",
                "chaoyang",
                "18:00-22:00",
                500,
                2,
                "relaxed",
                List.of("photo"),
                List.of("queue")
        ));
        StubIntentAgentService fallback = new StubIntentAgentService(
                new RoutePlanRequest("U10001", "fallback-scene", null, "fallback-district", "10:00-12:00", 100, 1, "slow", List.of(), List.of()),
                IntentAgentService.ParsedAdjustment.change(new ChangeRequest(ChangeType.LOCK_STOP, 1, null, null, null, List.of(), List.of(), List.of()))
        );
        SpringAiIntentAgentService service = new SpringAiIntentAgentService(
                gateway,
                fallback,
                sampleReferenceData(),
                "plan-system",
                "adjust-system"
        );

        RoutePlanRequest parsed = service.parsePlanRequest("U10001", "Need a citywalk tonight.");

        assertThat(parsed.scene()).isEqualTo("Citywalk");
        assertThat(parsed.businessArea()).isEqualTo("sanlitun");
        assertThat(parsed.district()).isEqualTo("chaoyang");
        assertThat(parsed.budgetTotal()).isEqualTo(500);
        assertThat(parsed.partySize()).isEqualTo(2);
        assertThat(parsed.preferTags()).containsExactly("photo");
    }

    @Test
    void invalidPlanOutputFallsBackSafely() {
        RoutePlanRequest fallbackRequest = new RoutePlanRequest(
                "U10002",
                "Citywalk",
                null,
                "chaoyang",
                "13:00-19:00",
                260,
                1,
                "relaxed",
                List.of("walk"),
                List.of()
        );
        SpringAiIntentAgentService service = new SpringAiIntentAgentService(
                new CapturingGateway(new SpringAiIntentAgentService.PlanIntentLlmResponse(
                        "unsupported-scene",
                        "unknown-area",
                        "unknown-district",
                        "bad-window",
                        -1,
                        0,
                        null,
                        List.of(),
                        List.of()
                )),
                new StubIntentAgentService(
                        fallbackRequest,
                        IntentAgentService.ParsedAdjustment.change(new ChangeRequest(ChangeType.LOCK_STOP, 1, null, null, null, List.of(), List.of(), List.of()))
                ),
                sampleReferenceData(),
                "plan-system",
                "adjust-system"
        );

        RoutePlanRequest parsed = service.parsePlanRequest("U10002", "Need anything safe.");

        assertThat(parsed).isEqualTo(fallbackRequest);
    }

    @Test
    void datingPlanOutputIsNormalizedTowardFeasibleFallback() {
        RoutePlanRequest fallbackRequest = new RoutePlanRequest(
                "U10005",
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
        SpringAiIntentAgentService service = new SpringAiIntentAgentService(
                new CapturingGateway(new SpringAiIntentAgentService.PlanIntentLlmResponse(
                        "dating",
                        "sanlitun",
                        "chaoyang",
                        "19:00-21:00",
                        500,
                        2,
                        "tight",
                        List.of("photo", "romantic", "night view"),
                        List.of("expensive", "crowded")
                )),
                new StubIntentAgentService(
                        fallbackRequest,
                        IntentAgentService.ParsedAdjustment.change(new ChangeRequest(ChangeType.LOCK_STOP, 1, null, null, null, List.of(), List.of(), List.of()))
                ),
                sampleReferenceData(),
                "plan-system",
                "adjust-system"
        );

        IntentAgentService.PlanParseResult parseResult = service.parsePlanRequestResult("U10005", "Tonight date in Sanlitun.");

        assertThat(parseResult.diagnosticSummary()).contains("fake=").contains("spring_ai=");
        assertThat(parseResult.primaryRequest()).isEqualTo(new RoutePlanRequest(
                "U10005",
                "dating",
                null,
                "chaoyang",
                "18:00-22:00",
                500,
                2,
                "relaxed",
                List.of("photo"),
                List.of()
        ));
        assertThat(parseResult.fallbackRequest()).isEqualTo(fallbackRequest);
    }

    @Test
    void adjustmentPromptUsesCompactContextOnly() {
        CapturingGateway gateway = new CapturingGateway(new SpringAiIntentAgentService.AdjustmentIntentLlmResponse(
                "CHANGE_REQUEST",
                "LOCK_STOP",
                2,
                null,
                null,
                null,
                List.of(),
                List.of(),
                null,
                null
        ));
        SpringAiIntentAgentService service = new SpringAiIntentAgentService(
                gateway,
                new StubIntentAgentService(
                        new RoutePlanRequest("U10003", "Citywalk", null, "chaoyang", "13:00-19:00", 260, 1, "relaxed", List.of(), List.of()),
                        IntentAgentService.ParsedAdjustment.change(new ChangeRequest(ChangeType.LOCK_STOP, 1, null, null, null, List.of(), List.of(), List.of()))
                ),
                sampleReferenceData(),
                "plan-system",
                "adjust-system"
        );

        IntentAgentService.ParsedAdjustment parsed = service.parseAdjustment("keep the second stop fixed", sampleRouteContext());

        assertThat(parsed.changeRequest().changeType()).isEqualTo(ChangeType.LOCK_STOP);
        assertThat(gateway.lastUserPrompt()).contains("latest_user_message: keep the second stop fixed");
        assertThat(gateway.lastUserPrompt()).contains("stop_order=1, slot_role=dinner, poi_name=Dinner A, business_area=sanlitun");
        assertThat(gateway.lastUserPrompt()).contains("locked_stop_orders: [2]");
        assertThat(gateway.lastUserPrompt()).contains("pending_clarification: question=Which stop should be changed?");
        assertThat(gateway.lastUserPrompt()).doesNotContain("P00001");
        assertThat(gateway.lastUserPrompt()).doesNotContain("poi_id=");
        assertThat(gateway.lastUserPrompt()).doesNotContain("arrive_time=");
        assertThat(gateway.lastUserPrompt()).doesNotContain("leave_time=");
    }

    @Test
    void invalidAdjustmentOutputFallsBackSafely() {
        IntentAgentService.ParsedAdjustment fallbackAdjustment = IntentAgentService.ParsedAdjustment.clarification(
                new ClarificationAnswer(1, null)
        );
        SpringAiIntentAgentService service = new SpringAiIntentAgentService(
                new CapturingGateway(new SpringAiIntentAgentService.AdjustmentIntentLlmResponse(
                        "CHANGE_REQUEST",
                        "NOT_A_REAL_CHANGE",
                        99,
                        null,
                        null,
                        null,
                        List.of(),
                        List.of(),
                        null,
                        null
                )),
                new StubIntentAgentService(
                        new RoutePlanRequest("U10004", "Citywalk", null, "chaoyang", "13:00-19:00", 260, 1, "relaxed", List.of(), List.of()),
                        fallbackAdjustment
                ),
                sampleReferenceData(),
                "plan-system",
                "adjust-system"
        );

        IntentAgentService.ParsedAdjustment parsed = service.parseAdjustment("answer the clarification", sampleRouteContext());

        assertThat(parsed).isEqualTo(fallbackAdjustment);
    }

    private IntentReferenceData sampleReferenceData() {
        return new IntentReferenceData(
                Set.of("Citywalk", "dating"),
                Set.of("sanlitun", "wangjing"),
                Set.of("chaoyang", "dongcheng"),
                Map.of("sanlitun", "chaoyang", "wangjing", "chaoyang")
        );
    }

    private CompactRouteContext sampleRouteContext() {
        return new CompactRouteContext(
                "S10001",
                "U10001",
                new RouteSessionIntent("Citywalk", "sanlitun", "chaoyang", "18:00-22:00", 500, 2, "relaxed", List.of("photo"), List.of("queue")),
                "Citywalk | 2 stops | budget 260",
                List.of(
                        new CompactRouteStop(1, "dinner", "P00001", "Dinner A", "sanlitun", "18:00", "19:00"),
                        new CompactRouteStop(2, "walk", "P00002", "Walk A", "sanlitun", "19:20", "20:20")
                ),
                Set.of(2),
                new PendingClarification(
                        "Which stop should be changed?",
                        List.of("target_stop_order"),
                        List.of("1|dinner|Dinner A", "2|walk|Walk A"),
                        "replace it",
                        LocalDateTime.of(2026, 5, 29, 15, 0),
                        new ChangeRequest(ChangeType.REPLACE_STOP, null, null, null, null, List.of(), List.of(), List.of())
                ),
                List.of(new CompactChangeHistoryItem(
                        "C10001",
                        "LOCK_STOP",
                        2,
                        "Citywalk / 2 stops / 260",
                        "Citywalk / 2 stops / 260",
                        LocalDateTime.of(2026, 5, 29, 14, 0)
                )),
                4L
        );
    }

    private static final class CapturingGateway implements StructuredIntentParsingGateway {

        private final Object response;
        private String lastSystemPrompt;
        private String lastUserPrompt;

        private CapturingGateway(Object response) {
            this.response = response;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T call(String systemPrompt, String userPrompt, Class<T> responseType) {
            this.lastSystemPrompt = systemPrompt;
            this.lastUserPrompt = userPrompt;
            return (T) response;
        }

        private String lastUserPrompt() {
            return lastUserPrompt;
        }
    }

    private static final class StubIntentAgentService implements IntentAgentService {

        private final RoutePlanRequest planRequest;
        private final ParsedAdjustment parsedAdjustment;

        private StubIntentAgentService(RoutePlanRequest planRequest, ParsedAdjustment parsedAdjustment) {
            this.planRequest = planRequest;
            this.parsedAdjustment = parsedAdjustment;
        }

        @Override
        public RoutePlanRequest parsePlanRequest(String userId, String message) {
            return planRequest;
        }

        @Override
        public ParsedAdjustment parseAdjustment(String message, CompactRouteContext routeContext) {
            return parsedAdjustment;
        }
    }
}
