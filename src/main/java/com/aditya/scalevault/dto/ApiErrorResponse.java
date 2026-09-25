package com.aditya.scalevault.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(
    boolean success,
    String code,
    String message,
    Instant timestamp,
    String path,
    List<ValidationErrorDetail> errors
) {
    public static ApiErrorResponse of(String code, String message, String path) {
        return new ApiErrorResponse(false, code, message, Instant.now(), path, null);
    }

    public static ApiErrorResponse of(String code, String message, String path, List<ValidationErrorDetail> errors) {
        return new ApiErrorResponse(false, code, message, Instant.now(), path, errors);
    }

    public record ValidationErrorDetail(String field, String message) {}
}
