package com.mtai.mtairouteplanner.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mtai.mtairouteplanner.data.MockDataDatabaseRepository;
import com.mtai.mtairouteplanner.data.MockDataLoader;
import com.mtai.mtairouteplanner.data.PostgresMockDataSourceReader;
import com.mtai.mtairouteplanner.ai.FakeIntentAgentService;
import com.mtai.mtairouteplanner.ai.FakePresenterService;
import com.mtai.mtairouteplanner.ai.IntentAgentService;
import com.mtai.mtairouteplanner.ai.IntentReferenceData;
import com.mtai.mtairouteplanner.ai.PresenterAgentService;
import com.mtai.mtairouteplanner.ai.SpringAiChatClientPresenterGateway;
import com.mtai.mtairouteplanner.ai.SpringAiChatClientIntentParsingGateway;
import com.mtai.mtairouteplanner.ai.SpringAiIntentAgentService;
import com.mtai.mtairouteplanner.ai.SpringAiPresenterAgentService;
import com.mtai.mtairouteplanner.service.ClarificationService;
import com.mtai.mtairouteplanner.service.InMemoryRouteSessionStore;
import com.mtai.mtairouteplanner.service.RedisRouteSessionStore;
import com.mtai.mtairouteplanner.service.RouteAdjustmentService;
import com.mtai.mtairouteplanner.service.RouteContextAssembler;
import com.mtai.mtairouteplanner.service.RouteOptimizerService;
import com.mtai.mtairouteplanner.service.RouteSessionService;
import com.mtai.mtairouteplanner.service.RouteSessionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.FileCopyUtils;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

@Configuration
public class DevRouteApiConfig {

    private static final Logger log = LoggerFactory.getLogger(DevRouteApiConfig.class);

    @Bean
    public FakeIntentAgentService fakeIntentAgentService() {
        return new FakeIntentAgentService();
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "route.intent.agent", havingValue = "fake", matchIfMissing = true)
    public IntentAgentService fakeModeIntentAgentService(FakeIntentAgentService fakeIntentAgentService) {
        return fakeIntentAgentService;
    }

    @Bean
    @ConditionalOnProperty(name = "route.data.source", havingValue = "json", matchIfMissing = true)
    public MockDataLoader jsonMockDataLoader() {
        return new MockDataLoader();
    }

    @Bean
    @ConditionalOnProperty(name = "route.data.source", havingValue = "postgres")
    public MockDataLoader postgresMockDataLoader(MockDataDatabaseRepository mockDataDatabaseRepository) {
        return new MockDataLoader(new PostgresMockDataSourceReader(mockDataDatabaseRepository));
    }

    @Bean
    public IntentReferenceData intentReferenceData(MockDataLoader mockDataLoader) {
        return IntentReferenceData.load(mockDataLoader);
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "route.intent.agent", havingValue = "spring-ai")
    public IntentAgentService springAiIntentAgentService(
            ObjectProvider<ChatClient.Builder> chatClientBuilderProvider,
            FakeIntentAgentService fakeIntentAgentService,
            IntentReferenceData intentReferenceData
    ) {
        ChatClient.Builder chatClientBuilder = chatClientBuilderProvider.getIfAvailable();
        if (chatClientBuilder == null) {
            log.warn("route.intent.agent=spring-ai but no ChatClient.Builder is available. Falling back to FakeIntentAgentService.");
            return fakeIntentAgentService;
        }
        return new SpringAiIntentAgentService(
                new SpringAiChatClientIntentParsingGateway(chatClientBuilder.build()),
                fakeIntentAgentService,
                intentReferenceData,
                readClasspathText("prompts/intent-plan-system.st"),
                readClasspathText("prompts/intent-adjust-system.st")
        );
    }

    @Bean
    public FakePresenterService fakePresenterService() {
        return new FakePresenterService();
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "route.presenter.agent", havingValue = "fake", matchIfMissing = true)
    public PresenterAgentService fakeModePresenterAgentService(FakePresenterService fakePresenterService) {
        return fakePresenterService;
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "route.presenter.agent", havingValue = "spring-ai")
    public PresenterAgentService springAiPresenterAgentService(
            ObjectProvider<ChatClient.Builder> chatClientBuilderProvider,
            FakePresenterService fakePresenterService
    ) {
        ChatClient.Builder chatClientBuilder = chatClientBuilderProvider.getIfAvailable();
        if (chatClientBuilder == null) {
            log.warn("route.presenter.agent=spring-ai but no ChatClient.Builder is available. Falling back to FakePresenterService.");
            return fakePresenterService;
        }
        return new SpringAiPresenterAgentService(
                new SpringAiChatClientPresenterGateway(chatClientBuilder.build()),
                fakePresenterService,
                readClasspathText("prompts/presenter-system.st")
        );
    }

    @Bean
    @ConditionalOnProperty(name = "route.session.store", havingValue = "memory", matchIfMissing = true)
    public RouteSessionStore inMemoryRouteSessionStore() {
        return new InMemoryRouteSessionStore();
    }

    @Bean
    @ConditionalOnProperty(name = "route.session.store", havingValue = "redis")
    public RouteSessionStore redisRouteSessionStore(StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper) {
        return new RedisRouteSessionStore(stringRedisTemplate, objectMapper);
    }

    @Bean
    public RouteSessionService routeSessionService(RouteSessionStore routeSessionStore) {
        return new RouteSessionService(routeSessionStore);
    }

    @Bean
    public RouteOptimizerService routeOptimizerService(MockDataLoader mockDataLoader) {
        return new RouteOptimizerService(mockDataLoader);
    }

    @Bean
    public RouteAdjustmentService routeAdjustmentService(RouteSessionService routeSessionService, MockDataLoader mockDataLoader) {
        return new RouteAdjustmentService(mockDataLoader, routeSessionService);
    }

    @Bean
    public RouteContextAssembler routeContextAssembler() {
        return new RouteContextAssembler();
    }

    @Bean
    public ClarificationService clarificationService(RouteSessionService routeSessionService) {
        return new ClarificationService(routeSessionService);
    }

    private String readClasspathText(String classpathLocation) {
        ClassPathResource resource = new ClassPathResource(classpathLocation);
        try (InputStreamReader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
            return FileCopyUtils.copyToString(reader);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read prompt resource: " + classpathLocation, exception);
        }
    }
}
