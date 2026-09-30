package com.aditya.scalevault.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;

@Schema(description = "Standard paginated response wrapper")
public record PageResponse<T>(
    @Schema(description = "Page elements")
    List<T> content,

    @Schema(description = "Zero-indexed page number", example = "0")
    int page,

    @Schema(description = "Page size", example = "20")
    int size,

    @Schema(description = "Total number of elements across all pages", example = "100")
    long totalElements,

    @Schema(description = "Total number of pages", example = "5")
    int totalPages,

    @Schema(description = "Whether this is the first page", example = "true")
    boolean first,

    @Schema(description = "Whether this is the last page", example = "false")
    boolean last
) {
    public static <T> PageResponse<T> fromPage(Page<T> page) {
        return new PageResponse<>(
            page.getContent(),
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages(),
            page.isFirst(),
            page.isLast()
        );
    }
}
