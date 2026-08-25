package com.aditya.scalevault.dto;

import java.time.Instant;

public record HealthResponse(
    String status,
    String service,
    Instant timestamp
) {}
