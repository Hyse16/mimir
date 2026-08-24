package com.mimir.common;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.mimir.blog.BlogNotFoundException;
import com.mimir.blog.BlogPostService;
import com.mimir.blog.StaleDraftVersionException;

@RestControllerAdvice(basePackageClasses = BlogPostService.class)
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> validation(MethodArgumentNotValidException exception) {
        List<FieldErrorResponse> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldErrorResponse(error.getField(), error.getCode()))
                .toList();
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed.", fieldErrors);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiErrorResponse> badRequest(IllegalArgumentException exception) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", exception.getMessage(), List.of());
    }

    @ExceptionHandler(BlogNotFoundException.class)
    ResponseEntity<ApiErrorResponse> notFound() {
        return response(HttpStatus.NOT_FOUND, "BLOG_NOT_FOUND", "Blog post was not found.", List.of());
    }

    @ExceptionHandler(StaleDraftVersionException.class)
    ResponseEntity<ApiErrorResponse> conflict(StaleDraftVersionException exception) {
        return response(HttpStatus.CONFLICT, "STALE_DRAFT_VERSION", exception.getMessage(), List.of());
    }

    @ExceptionHandler(RuntimeException.class)
    ResponseEntity<ApiErrorResponse> runtime() {
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred.", List.of());
    }

    private ResponseEntity<ApiErrorResponse> response(
            HttpStatus status,
            String code,
            String message,
            List<FieldErrorResponse> fieldErrors) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(
                code,
                message,
                fieldErrors,
                UUID.randomUUID().toString()));
    }

    record ApiErrorResponse(
            String code,
            String message,
            List<FieldErrorResponse> fieldErrors,
            String traceId) {
    }

    record FieldErrorResponse(String field, String reason) {
    }
}
