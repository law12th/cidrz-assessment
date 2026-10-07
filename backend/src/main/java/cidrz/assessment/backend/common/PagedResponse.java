package cidrz.assessment.backend.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * A page of results, in the shape the brief fixes (appendix 15.3):
 * {@code { "page", "page_size", "total", "items" }}.
 *
 * <p>The kwasila {@code PagedResponse} uses {@code content}/{@code totalElements}/{@code totalPages}
 * /{@code currentPage}/{@code size}/{@code hasNext}/{@code hasPrevious}. The published contract wins
 * over the house names here; {@code hasNext} and {@code hasPrevious} are derivable from the three
 * numbers, so nothing is lost. Mutable with a no-args constructor (as kwasila response DTOs are) so
 * tests can deserialise it.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Paged response")
public class PagedResponse<T> {

    @Schema(description = "1-based page number", example = "1")
    private int page;

    @Schema(description = "Rows requested per page", example = "50")
    private int pageSize;

    @Schema(description = "Rows matching the filter, across every page", example = "10342")
    private long total;

    @Schema(description = "This page's rows")
    private List<T> items;
}
