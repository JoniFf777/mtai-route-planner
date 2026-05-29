package com.mtai.mtairouteplanner.ai;

public interface StructuredIntentParsingGateway {

    <T> T call(String systemPrompt, String userPrompt, Class<T> responseType);
}
