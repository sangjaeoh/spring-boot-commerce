package com.commerce.common.web.paging;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

@Schema(description = "페이지 메타. 페이지 번호는 1부터 시작한다.")
public record PaginationResponse(
        @Schema(description = "현재 페이지 번호(1부터 시작)") int number,
        @Schema(description = "페이지 크기") int size,
        @Schema(description = "전체 요소 수") long totalElements,
        @Schema(description = "전체 페이지 수") int totalPages) {

    /** 조회 결과 {@link Page}에서 페이지 메타를 만든다. 0-based 페이지 번호는 1-based로 바꿔 싣는다. */
    public static PaginationResponse from(Page<?> page) {
        return new PaginationResponse(
                page.getNumber() + 1, page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
