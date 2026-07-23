package com.commerce.app.admin.web.v1.admin.inquiry.response;

import com.commerce.common.web.paging.PaginationResponse;
import com.commerce.domain.inquiry.application.info.InquiryInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.data.domain.Page;

@Schema(description = "관리자용 문의 목록 페이지")
public record InquiryPageResponse(
        @Schema(description = "문의 목록(최신 문의 우선)") List<InquiryResponse> inquiries,
        @Schema(description = "페이지 메타") PaginationResponse page) {

    public InquiryPageResponse {
        inquiries = List.copyOf(inquiries);
    }

    /** 문의 조회 페이지에서 응답을 만든다. */
    public static InquiryPageResponse from(Page<InquiryInfo> page) {
        return new InquiryPageResponse(
                page.getContent().stream().map(InquiryResponse::from).toList(), PaginationResponse.from(page));
    }
}
