package com.mtai.mtairouteplanner.ai.presenter;

import com.mtai.mtairouteplanner.model.adjustment.AdjustmentResult;
import com.mtai.mtairouteplanner.model.adjustment.AdjustmentStatus;
import com.mtai.mtairouteplanner.model.route.GeneratedRoutePlan;
import com.mtai.mtairouteplanner.model.route.GeneratedRouteStop;
import com.mtai.mtairouteplanner.model.session.RouteSessionState;
import com.mtai.mtairouteplanner.model.session.RouteSessionStatus;
import com.mtai.mtairouteplanner.model.route.RouteValidationIssue;
import com.mtai.mtairouteplanner.model.route.RouteValidationResult;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SpringAiPresenterAgentServiceTest {

    @Test
    void initialRoutePromptUsesCompactFinalizedRouteDataOnly() {
        CapturingGateway gateway = new CapturingGateway("这是 AI 路线说明。");
        SpringAiPresenterAgentService service = new SpringAiPresenterAgentService(
                gateway,
                new FakePresenterService(),
                "presenter-system"
        );

        String message = service.presentInitialRoute(sampleRouteSessionState(Set.of(2)));

        assertThat(message).isEqualTo("这是 AI 路线说明。");
        assertThat(gateway.lastUserPrompt()).contains("presentation_type: INITIAL_ROUTE");
        assertThat(gateway.lastUserPrompt()).contains("stop_order=1");
        assertThat(gateway.lastUserPrompt()).contains("stop_name=Dinner A");
        assertThat(gateway.lastUserPrompt()).contains("slot_role=dinner");
        assertThat(gateway.lastUserPrompt()).contains("business_area=sanlitun");
        assertThat(gateway.lastUserPrompt()).contains("arrive_time=18:00");
        assertThat(gateway.lastUserPrompt()).contains("leave_time=19:20");
        assertThat(gateway.lastUserPrompt()).contains("matched_prefer_tags=[photo]");
        assertThat(gateway.lastUserPrompt()).contains("validation_issues:");
        assertThat(gateway.lastUserPrompt()).doesNotContain("P10001");
        assertThat(gateway.lastUserPrompt()).doesNotContain("current_intent");
        assertThat(gateway.lastUserPrompt()).doesNotContain("version");
        assertThat(gateway.lastUserPrompt()).doesNotContain("change_history");
    }

    @Test
    void blankPresenterOutputFallsBackToFakePresenter() {
        FakePresenterService fakePresenterService = new FakePresenterService();
        SpringAiPresenterAgentService service = new SpringAiPresenterAgentService(
                new CapturingGateway("   "),
                fakePresenterService,
                "presenter-system"
        );

        RouteSessionState routeSessionState = sampleRouteSessionState(Set.of(2));
        String message = service.presentInitialRoute(routeSessionState);

        assertThat(message).isEqualTo(fakePresenterService.presentInitialRoute(routeSessionState));
    }

    @Test
    void presenterFallsBackWhenOutputMentionsLockedStopsButInputHasNone() {
        FakePresenterService fakePresenterService = new FakePresenterService();
        RouteSessionState unlockedSessionState = sampleRouteSessionState(Set.of());
        SpringAiPresenterAgentService service = new SpringAiPresenterAgentService(
                new CapturingGateway("所有停靠点均正常锁定，可放心按原计划执行。"),
                fakePresenterService,
                "presenter-system"
        );

        String message = service.presentInitialRoute(unlockedSessionState);

        assertThat(message).doesNotContain("锁定");
        assertThat(message).isEqualTo(fakePresenterService.presentInitialRoute(unlockedSessionState));
    }

    @Test
    void presenterMayMentionLockedStopsWhenInputHasLocks() {
        SpringAiPresenterAgentService service = new SpringAiPresenterAgentService(
                new CapturingGateway("第二站已锁定，后续安排会优先保持不变。"),
                new FakePresenterService(),
                "presenter-system"
        );

        String message = service.presentInitialRoute(sampleRouteSessionState(Set.of(2)));

        assertThat(message).contains("锁定");
    }

    @Test
    void adjustmentPresenterCanBeUnitTestedWithGateway() {
        SpringAiPresenterAgentService service = new SpringAiPresenterAgentService(
                new CapturingGateway("这是调整后的说明。"),
                new FakePresenterService(),
                "presenter-system"
        );

        String message = service.presentAdjustmentResult(sampleAdjustmentResult());

        assertThat(message).isEqualTo("这是调整后的说明。");
    }

    private RouteSessionState sampleRouteSessionState(Set<Integer> lockedStopOrders) {
        GeneratedRoutePlan routePlan = sampleRoutePlan();
        return new RouteSessionState(
                "S10001",
                "U10001",
                RouteSessionStatus.ACTIVE,
                null,
                routePlan,
                lockedStopOrders,
                null,
                List.of(),
                3L,
                LocalDateTime.of(2026, 5, 29, 18, 0)
        );
    }

    private AdjustmentResult sampleAdjustmentResult() {
        return new AdjustmentResult(
                "S10001",
                AdjustmentStatus.SUCCESS,
                "Adjusted",
                sampleRouteSessionState(Set.of(2)),
                sampleRoutePlan()
        );
    }

    private GeneratedRoutePlan sampleRoutePlan() {
        return new GeneratedRoutePlan(
                "T10001",
                "dating",
                "18:00-22:00",
                480,
                220,
                5.4,
                92.0,
                "18:00",
                "21:40",
                List.of(
                        new GeneratedRouteStop(
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
                        ),
                        new GeneratedRouteStop(
                                2,
                                "walk",
                                "P10002",
                                "Walk A",
                                "liangma river",
                                "chaoyang",
                                116.4722,
                                39.9491,
                                "GCJ-02",
                                "sightseeing",
                                "outdoor",
                                "19:40",
                                "20:40",
                                60,
                                20.0,
                                3.2,
                                0,
                                30.0,
                                List.of("photo"),
                                List.of("queue")
                        )
                ),
                new RouteValidationResult(false, List.of(
                        new RouteValidationIssue("LATE", "Arrives slightly late to the final stop", 2)
                ))
        );
    }

    private static final class CapturingGateway implements PresenterGenerationGateway {

        private final String response;
        private String lastUserPrompt;

        private CapturingGateway(String response) {
            this.response = response;
        }

        @Override
        public String generate(String systemPrompt, String userPrompt) {
            this.lastUserPrompt = userPrompt;
            return response;
        }

        private String lastUserPrompt() {
            return lastUserPrompt;
        }
    }
}
