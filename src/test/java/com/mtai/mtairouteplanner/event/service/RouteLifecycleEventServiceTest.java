package com.mtai.mtairouteplanner.event.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mtai.mtairouteplanner.event.model.RouteEventType;
import com.mtai.mtairouteplanner.event.model.RouteLifecycleEvent;
import com.mtai.mtairouteplanner.event.publisher.NoopRouteEventPublisher;
import com.mtai.mtairouteplanner.event.publisher.RouteEventPublisher;
import com.mtai.mtairouteplanner.model.adjustment.AdjustmentResult;
import com.mtai.mtairouteplanner.model.adjustment.AdjustmentStatus;
import com.mtai.mtairouteplanner.model.adjustment.ChangeRequest;
import com.mtai.mtairouteplanner.model.adjustment.ChangeType;
import com.mtai.mtairouteplanner.model.clarification.ClarificationResolutionResult;
import com.mtai.mtairouteplanner.model.route.GeneratedRoutePlan;
import com.mtai.mtairouteplanner.model.route.GeneratedRouteStop;
import com.mtai.mtairouteplanner.model.session.RouteSessionIntent;
import com.mtai.mtairouteplanner.model.session.RouteSessionState;
import com.mtai.mtairouteplanner.model.session.RouteSessionStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class RouteLifecycleEventServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void noopPublisherWorksWithoutThrowing() {
        RouteLifecycleEvent event = new RouteLifecycleEvent(
                "EVT-1",
                RouteEventType.ROUTE_PLANNED,
                "S10001",
                "U10001",
                "dating",
                "SUCCESS",
                null,
                null,
                null,
                LocalDateTime.now()
        );

        assertThatCode(() -> new NoopRouteEventPublisher().publish(event)).doesNotThrowAnyException();
    }

    @Test
    void plannedEventPayloadStaysCompact() throws Exception {
        CapturingPublisher capturingPublisher = new CapturingPublisher();
        RouteLifecycleEventService eventService = new RouteLifecycleEventService(capturingPublisher);

        eventService.publishRoutePlanned(sampleSessionState());

        RouteLifecycleEvent event = capturingPublisher.singleEvent();
        String json = objectMapper.writeValueAsString(event);
        assertThat(event.eventType()).isEqualTo(RouteEventType.ROUTE_PLANNED);
        assertThat(event.routeSummary()).isNotNull();
        assertThat(event.routeSummary().stopNames()).containsExactly("Dinner A", "Cafe B");
        assertThat(json).doesNotContain("current_route");
        assertThat(json).doesNotContain("pending_clarification");
        assertThat(json).doesNotContain("change_history");
        assertThat(json).doesNotContain("locked_stop_orders");
        assertThat(json).doesNotContain("embedding");
        assertThat(json).doesNotContain("ugc");
        assertThat(json).doesNotContain("review_summary");
    }

    @Test
    void publisherFailuresAreSwallowedSafely() {
        RouteLifecycleEventService eventService = new RouteLifecycleEventService(event -> {
            throw new IllegalStateException("rocketmq down");
        });

        assertThatCode(() -> eventService.publishRoutePlanned(sampleSessionState())).doesNotThrowAnyException();
    }

    @Test
    void clarificationResolvedEventIncludesOnlyCompactFields() {
        CapturingPublisher capturingPublisher = new CapturingPublisher();
        RouteLifecycleEventService eventService = new RouteLifecycleEventService(capturingPublisher);
        RouteSessionState sessionState = sampleSessionState();

        eventService.publishClarificationResolved(new ClarificationResolutionResult(
                sessionState.sessionId(),
                "RESOLVED",
                "Clarification resolved successfully.",
                new ChangeRequest(ChangeType.REPLACE_STOP, 1, null, null, null, List.of(), List.of(), null),
                sessionState,
                sessionState.currentRoute()
        ));

        RouteLifecycleEvent event = capturingPublisher.singleEvent();
        assertThat(event.eventType()).isEqualTo(RouteEventType.ROUTE_CLARIFICATION_RESOLVED);
        assertThat(event.changeType()).isEqualTo("REPLACE_STOP");
        assertThat(event.routeSummary().stopCount()).isEqualTo(2);
    }

    @Test
    void adjustmentFailureCarriesMessageButNotFullState() throws Exception {
        CapturingPublisher capturingPublisher = new CapturingPublisher();
        RouteLifecycleEventService eventService = new RouteLifecycleEventService(capturingPublisher);
        RouteSessionState sessionState = sampleSessionState();

        eventService.publishRouteAdjustmentFailed(new AdjustmentResult(
                sessionState.sessionId(),
                AdjustmentStatus.FAILED,
                "No feasible adjusted route found.",
                sessionState,
                null
        ), ChangeType.LOWER_BUDGET);

        RouteLifecycleEvent event = capturingPublisher.singleEvent();
        String json = objectMapper.writeValueAsString(event);
        assertThat(event.eventType()).isEqualTo(RouteEventType.ROUTE_ADJUSTMENT_FAILED);
        assertThat(event.issueReason()).contains("No feasible adjusted route");
        assertThat(event.changeType()).isEqualTo("LOWER_BUDGET");
        assertThat(json).doesNotContain("current_intent");
        assertThat(json).doesNotContain("session_state");
    }

    private RouteSessionState sampleSessionState() {
        return new RouteSessionState(
                "S10001",
                "U10001",
                RouteSessionStatus.ACTIVE,
                new RouteSessionIntent(
                        "dating",
                        "sanlitun",
                        "chaoyang",
                        "18:00-22:00",
                        500,
                        2,
                        "relaxed",
                        List.of("photo"),
                        List.of("queue")
                ),
                new GeneratedRoutePlan(
                        "T10001",
                        "dating",
                        "18:00-22:00",
                        460,
                        220,
                        4.6,
                        91.0,
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
                                        "19:15",
                                        75,
                                        0.0,
                                        0.0,
                                        260,
                                        48.0,
                                        List.of("photo"),
                                        List.of()
                                ),
                                new GeneratedRouteStop(
                                        2,
                                        "coffee",
                                        "P10002",
                                        "Cafe B",
                                        "sanlitun",
                                        "chaoyang",
                                        116.4599,
                                        39.9368,
                                        "GCJ-02",
                                        "coffee",
                                        "indoor",
                                        "19:30",
                                        "20:20",
                                        50,
                                        15.0,
                                        1.1,
                                        90,
                                        43.0,
                                        List.of("photo"),
                                        List.of()
                                )
                        ),
                        null
                ),
                Set.of(2),
                null,
                List.of(),
                3L,
                LocalDateTime.now()
        );
    }

    private static final class CapturingPublisher implements RouteEventPublisher {

        private final List<RouteLifecycleEvent> events = new ArrayList<>();

        @Override
        public void publish(RouteLifecycleEvent event) {
            events.add(event);
        }

        private RouteLifecycleEvent singleEvent() {
            assertThat(events).hasSize(1);
            return events.getFirst();
        }
    }
}


