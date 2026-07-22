package com.commerce.inquiry.info;

import com.commerce.inquiry.entity.Inquiry;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 문의 한 건의 조회 경계 모델이다. 비밀글 마스킹은 노출 계층이 담당한다. */
public record InquiryInfo(
        UUID id,
        UUID memberId,
        UUID productId,
        String content,
        boolean secret,
        @Nullable String answer,
        Instant writtenAt) {

    /** 문의 엔티티에서 조회 모델을 만든다. */
    public static InquiryInfo from(Inquiry inquiry) {
        return new InquiryInfo(
                inquiry.getId(),
                inquiry.getMemberId(),
                inquiry.getProductId(),
                inquiry.getContent(),
                inquiry.isSecret(),
                inquiry.getAnswer(),
                inquiry.getCreatedAt());
    }
}
