package com.commerce.web.paging;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Schema(description = "페이지 파라미터. 페이지 번호는 1부터 시작한다.")
public record PaginationRequest(
        @Schema(description = "페이지 번호(1부터 시작, 생략 시 1)", defaultValue = "1") @Min(1)
        Integer page,

        @Schema(description = "페이지 크기(생략 시 20, 최대 100)", defaultValue = "20") @Min(1) @Max(100)
        Integer size) {

    // 생성자 바인딩은 미전달 파라미터를 null로 넘기므로 기본값(page=1·size=20) 보정은 여기가 유일한 경로다.
    public PaginationRequest {
        page = page == null ? 1 : page;
        size = size == null ? 20 : size;
    }

    /** 1-based 페이지 번호를 도메인 {@code Pageable}이 쓰는 0-based 번호로 바꿔 반환한다. */
    public int zeroBasedPage() {
        return page - 1;
    }
}
