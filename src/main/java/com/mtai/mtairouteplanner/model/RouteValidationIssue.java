package com.mtai.mtairouteplanner.model;

public record RouteValidationIssue(
        String code,
        String message,
        Integer stopOrder
) {
}
