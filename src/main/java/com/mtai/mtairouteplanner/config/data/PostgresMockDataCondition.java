package com.mtai.mtairouteplanner.config.data;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class PostgresMockDataCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String dataSource = context.getEnvironment().getProperty("route.data.source", "json");
        boolean loadToDb = Boolean.parseBoolean(context.getEnvironment().getProperty("mock-data.load-to-db", "false"));
        return "postgres".equalsIgnoreCase(dataSource) || loadToDb;
    }
}

