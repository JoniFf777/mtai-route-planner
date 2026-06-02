package com.mtai.mtairouteplanner.event.publisher;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mtai.mtairouteplanner.event.model.RouteLifecycleEvent;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RocketMqRouteEventPublisher implements RouteEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RocketMqRouteEventPublisher.class);

    private final RocketMQTemplate rocketMQTemplate;
    private final ObjectMapper objectMapper;
    private final String topic;

    public RocketMqRouteEventPublisher(RocketMQTemplate rocketMQTemplate, ObjectMapper objectMapper, String topic) {
        this.rocketMQTemplate = rocketMQTemplate;
        this.objectMapper = objectMapper;
        this.topic = topic;
    }

    @Override
    public void publish(RouteLifecycleEvent event) {
        String payload = toJson(event);
        rocketMQTemplate.asyncSend(topic, payload, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("Published route event to RocketMQ. topic={} eventType={} eventId={}",
                        topic, event.eventType(), event.eventId());
            }

            @Override
            public void onException(Throwable throwable) {
                log.warn("RocketMQ async publish failed. topic={} eventType={} eventId={}",
                        topic, event.eventType(), event.eventId(), throwable);
            }
        });
    }

    private String toJson(RouteLifecycleEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize route lifecycle event.", exception);
        }
    }
}


