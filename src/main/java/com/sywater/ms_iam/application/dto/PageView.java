package com.sywater.ms_iam.application.dto;

import java.util.List;
import java.util.function.Function;

/** One page of a list. Pages start at 0. */
public record PageView<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

    public static <T> PageView<T> of(List<T> items, int page, int size, long totalItems) {
        int pages = size <= 0 ? 0 : (int) ((totalItems + size - 1) / size);
        return new PageView<>(items, page, size, totalItems, pages);
    }

    public <R> PageView<R> map(Function<T, R> mapper) {
        return new PageView<>(items.stream().map(mapper).toList(), page, size, totalItems, totalPages);
    }
}
