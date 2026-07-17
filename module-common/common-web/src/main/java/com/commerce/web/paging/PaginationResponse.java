package com.commerce.web.paging;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

/**
 * 공용 페이지 메타 응답이다. 페이지 응답 DTO가 목록 옆에 {@code page} 컴포넌트로 싣는다.
 * 클라이언트 계약은 1-based라 0-based {@link Page#getNumber()}의 +1 보정은 {@link #from(Page)}가
 * 유일한 지점이다.
 */
@Schema(description = "페이지 메타. 페이지 번호는 1부터 시작한다.")
public record PaginationResponse(
        @Schema(description = "현재 페이지 번호(1부터 시작)") int number,
        @Schema(description = "페이지 크기") int size,
        @Schema(description = "전체 요소 수") long totalElements,
        @Schema(description = "전체 페이지 수") int totalPages) {

    public static PaginationResponse from(Page<?> page) {
        return new PaginationResponse(
                page.getNumber() + 1, page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
