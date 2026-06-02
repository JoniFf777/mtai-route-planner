package com.mtai.mtairouteplanner.model.route;

import java.util.List;

public record RouteValidationResult(
        boolean valid,
        List<RouteValidationIssue> issues
) {
    public RouteValidationResult {
        issues = issues == null ? List.of() : List.copyOf(issues);
    }
}


