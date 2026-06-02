package com.mtai.mtairouteplanner.ai.intent;

import org.springframework.ai.chat.client.ChatClient;

public class SpringAiChatClientIntentParsingGateway implements StructuredIntentParsingGateway {

    private final ChatClient chatClient;

    public SpringAiChatClientIntentParsingGateway(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public <T> T call(String systemPrompt, String userPrompt, Class<T> responseType) {
        return chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .entity(responseType);
    }
}

