package com.commerce.api.web.v1.inquiry.response;

import com.commerce.inquiry.info.InquiryInfo;
import com.commerce.web.auth.AuthUser;
import com.commerce.web.paging.PaginationResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;

@Schema(description = "상품 문의 페이지")
public record InquiryPageResponse(
        @Schema(description = "문의 목록(최신 문의 우선)") List<InquiryResponse> inquiries,
        @Schema(description = "페이지 메타") PaginationResponse page) {

    private static final String ADMIN_ROLE = "ADMIN";

    public InquiryPageResponse {
        inquiries = List.copyOf(inquiries);
    }

    /** 문의 조회 페이지에서 응답을 만든다. 비밀글은 작성자·관리자 열람자에게만 본문·답변을 싣는다. */
    public static InquiryPageResponse of(Page<InquiryInfo> page, @Nullable AuthUser viewer) {
        return new InquiryPageResponse(
                page.getContent().stream()
                        .map(info -> InquiryResponse.of(info, isReadable(info, viewer)))
                        .toList(),
                PaginationResponse.from(page));
    }

    /** 비밀글이 아니거나, 열람자가 작성자·관리자면 내용을 읽을 수 있다. */
    private static boolean isReadable(InquiryInfo info, @Nullable AuthUser viewer) {
        if (!info.secret()) {
            return true;
        }
        return viewer != null && (viewer.memberId().equals(info.memberId()) || ADMIN_ROLE.equals(viewer.role()));
    }
}
