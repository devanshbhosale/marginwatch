package com.devanshbhosale.marginwatch.dto;

import java.util.List;

/**
 * The one paged shape used across the API: {content, totalElements, totalPages, number, size}.
 */
public record PageRes<T>(List<T> content, long totalElements, int totalPages, int number, int size) {

    public static <S, T> PageRes<T> of(org.springframework.data.domain.Page<S> page, List<T> mapped) {
        return new PageRes<>(mapped, page.getTotalElements(), page.getTotalPages(), page.getNumber(),
                page.getSize());
    }
}
