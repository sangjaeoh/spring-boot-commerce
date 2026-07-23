package com.commerce.app.api.web.v1.inquiry.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "문의 작성 결과")
public record InquiryCreationResponse(
        @Schema(description = "작성된 문의 ID(문자열)") String inquiryId) {

    /** 작성된 문의 ID에서 응답을 만든다. */
    public static InquiryCreationResponse from(UUID inquiryId) {
        return new InquiryCreationResponse(inquiryId.toString());
    }
}
