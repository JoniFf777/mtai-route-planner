package com.mtai.mtairouteplanner.ai;

import org.springframework.ai.chat.client.ChatClient;

import java.util.Objects;

public class SpringAiChatClientPresenterGateway implements PresenterGenerationGateway {

    private final ChatClient chatClient;

    public SpringAiChatClientPresenterGateway(ChatClient chatClient) {
        this.chatClient = Objects.requireNonNull(chatClient, "chatClient must not be null");
    }

    @Override
    public String generate(String systemPrompt, String userPrompt) {
        return chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .content();
    }
}
