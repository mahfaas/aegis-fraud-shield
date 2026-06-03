package io.github.mahfaas.fraudshield.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Generic paginated response wrapper for any list-returning endpoint.
 *
 * <p>Using a Java {@code record} with a bounded type parameter {@code <T>} demonstrates:
 * <ul>
 *   <li>Generics — the same wrapper works for any content type without casting</li>
 *   <li>Immutability — records are inherently immutable</li>
 *   <li>DRY — one class replaces ad-hoc {@code Map<String, Object>} responses everywhere</li>
 * </ul>
 *
 * <p>Example response:
 * <pre>
 * {
 *   "content":       [...],
 *   "page":          0,
 *   "size":          20,
 *   "totalElements": 342,
 *   "totalPages":    18
 * }
 * </pre>
 *
 * @param <T> the type of items in {@code content}
 */
@Schema(description = "Generic paginated response wrapper")
public record PagedResponse<T>(

        @Schema(description = "Page content — list of items")
        List<T> content,

        @Schema(description = "Zero-based page index", example = "0")
        int page,

        @Schema(description = "Number of items per page", example = "20")
        int size,

        @Schema(description = "Total number of items across all pages", example = "342")
        long totalElements,

        @Schema(description = "Total number of pages", example = "18")
        int totalPages
) {
    /**
     * Convenience factory that computes {@code totalPages} automatically.
     *
     * @param content       the items on the current page
     * @param page          zero-based page index
     * @param size          requested page size
     * @param totalElements total items across all pages
     * @param <T>           item type
     * @return a fully populated {@link PagedResponse}
     */
    public static <T> PagedResponse<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPages = size == 0 ? 1 : (int) Math.ceil((double) totalElements / size);
        return new PagedResponse<>(List.copyOf(content), page, size, totalElements, totalPages);
    }
}
