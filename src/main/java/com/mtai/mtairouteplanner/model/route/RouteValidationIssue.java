package com.mtai.mtairouteplanner.model.route;

public record RouteValidationIssue(
        String code,
        String message,
        Integer stopOrder
) {
}


