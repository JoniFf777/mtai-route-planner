package com.mtai.mtairouteplanner.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

public final class MockDataObjectMapperFactory {

    private MockDataObjectMapperFactory() {
    }

    public static ObjectMapper create() {
        return new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    }
}
