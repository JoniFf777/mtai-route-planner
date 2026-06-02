package com.mtai.mtairouteplanner.controller;

import com.mtai.mtairouteplanner.controller.dto.ApiErrorResponse;
import com.mtai.mtairouteplanner.service.route.session.RouteSessionNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class DevRouteApiExceptionHandler {

    @ExceptionHandler(RouteSessionNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiErrorResponse handleSessionNotFound(RouteSessionNotFoundException exception) {
        return new ApiErrorResponse("SESSION_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleIllegalArgument(IllegalArgumentException exception) {
        return new ApiErrorResponse("INVALID_REQUEST", exception.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleUnreadableBody(HttpMessageNotReadableException exception) {
        return new ApiErrorResponse("INVALID_REQUEST", "Request body could not be parsed.");
    }
}

