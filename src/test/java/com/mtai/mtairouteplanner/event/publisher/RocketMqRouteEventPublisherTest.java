package com.mtai.mtairouteplanner.event.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mtai.mtairouteplanner.event.model.RouteEventRouteSummary;
import com.mtai.mtairouteplanner.event.model.RouteEventType;
import com.mtai.mtairouteplanner.event.model.RouteLifecycleEvent;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class RocketMqRouteEventPublisherTest {

    @Test
    void publisherSendsCompactJsonPayload() {
        RocketMQTemplate rocketMQTemplate = mock(RocketMQTemplate.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        CapturedPayload capturedPayload = new CapturedPayload();
        doAnswer(invocation -> {
            capturedPayload.topic = invocation.getArgument(0);
            capturedPayload.payload = invocation.getArgument(1);
            return null;
        }).when(rocketMQTemplate).asyncSend(eq("mtai-route-events"), any(String.class), any());

        RocketMqRouteEventPublisher publisher = new RocketMqRouteEventPublisher(
                rocketMQTemplate,
                objectMapper,
                "mtai-route-events"
        );

        publisher.publish(new RouteLifecycleEvent(
                "EVT-1",
                RouteEventType.ROUTE_PLANNED,
                "S10001",
                "U10001",
                "dating",
                "SUCCESS",
                null,
                new RouteEventRouteSummary("T10001", 2, 460, 220, 4.6, java.util.List.of("Dinner A", "Cafe B")),
                null,
                LocalDateTime.now()
        ));

        assertThat(capturedPayload.topic).isEqualTo("mtai-route-events");
        assertThat(capturedPayload.payload).contains("\"event_type\":\"ROUTE_PLANNED\"");
        assertThat(capturedPayload.payload).contains("\"route_summary\"");
        assertThat(capturedPayload.payload).doesNotContain("current_route");
        assertThat(capturedPayload.payload).doesNotContain("pending_clarification");
    }

    private static final class CapturedPayload {
        private String topic;
        private String payload;
    }
}


