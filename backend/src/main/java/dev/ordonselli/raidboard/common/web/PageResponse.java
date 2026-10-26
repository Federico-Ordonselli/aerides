package dev.ordonselli.raidboard.common.web;

import java.util.List;

import org.springframework.data.domain.Page;

/**
 * One page of results, as returned by the API.
 * <p>
 * Spring Data's {@code Page} is not serialized directly: its JSON shape is an implementation detail
 * that may change between versions. This record is the stable contract the frontend relies on.
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages());
    }
}
