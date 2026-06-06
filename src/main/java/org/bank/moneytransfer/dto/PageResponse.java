package org.bank.moneytransfer.dto;

import java.util.List;
import org.springframework.data.domain.Page;

public record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalItems,
        int totalPages,
        String correlationId
) {
    public static <T> PageResponse<T> from(Page<T> page, String correlationId) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                correlationId
        );
    }
}
