package com.argusiq.tracing.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice(assignableTypes = AlertController.class)
public class AlertExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ValidationErrorResponse> handleIllegalArgument(IllegalArgumentException exception) {
        return ResponseEntity.badRequest()
                .body(new ValidationErrorResponse("Invalid alert rule", List.of(exception.getMessage())));
    }

    public record ValidationErrorResponse(String message, List<String> errors) {
    }
}
