package com.aditya.scalevault.controller;

import com.aditya.scalevault.dto.ApiResponse;
import com.aditya.scalevault.dto.HealthResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/health")
@Tag(name = "Health", description = "Application health and availability endpoints")
public class HealthController {

    @GetMapping
    @Operation(summary = "Check application health", description = "Public health endpoint returning current service status")
    public ResponseEntity<ApiResponse<HealthResponse>> checkHealth() {
        HealthResponse response = new HealthResponse("UP", "ScaleVault", Instant.now());
        return ResponseEntity.ok(ApiResponse.success("ScaleVault is operational", response));
    }
}
