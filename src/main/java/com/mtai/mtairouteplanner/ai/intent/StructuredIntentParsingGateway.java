package com.mtai.mtairouteplanner.ai.intent;

public interface StructuredIntentParsingGateway {

    <T> T call(String systemPrompt, String userPrompt, Class<T> responseType);
}

