package com.mtai.mtairouteplanner.model;

import java.util.List;

public record RouteValidationResult(
        boolean valid,
        List<RouteValidationIssue> issues
) {
    public RouteValidationResult {
        issues = issues == null ? List.of() : List.copyOf(issues);
    }
}
