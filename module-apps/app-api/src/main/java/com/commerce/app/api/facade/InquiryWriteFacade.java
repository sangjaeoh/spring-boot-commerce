package com.commerce.app.api.facade;

import com.commerce.app.api.exception.ApiErrorCode;
import com.commerce.app.api.exception.ApiException;
import com.commerce.domain.inquiry.application.provided.InquiryAppender;
import com.commerce.domain.member.application.provided.MemberReader;
import com.commerce.domain.member.domain.MemberStatus;
import com.commerce.domain.product.application.provided.ProductReader;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** 문의 작성을 조율한다 — 자격 활성 회원과 실존 상품만 허용한다. */
@Component
public class InquiryWriteFacade {

    private final MemberReader memberReader;
    private final ProductReader productReader;
    private final InquiryAppender inquiryAppender;

    public InquiryWriteFacade(MemberReader memberReader, ProductReader productReader, InquiryAppender inquiryAppender) {
        this.memberReader = memberReader;
        this.productReader = productReader;
        this.inquiryAppender = inquiryAppender;
    }

    public UUID write(UUID memberId, UUID productId, String content, boolean secret) {
        // 1. 자격 확인 — 탈퇴 회원은 getMember가 부재로 거부한다
        if (memberReader.getMember(memberId).status() != MemberStatus.ACTIVE) {
            throw new ApiException(ApiErrorCode.INQUIRY_NOT_ELIGIBLE);
        }
        // 2. 상품 존재·미삭제 확인 — 숨김 상품은 통과한다(구매 후 숨김된 상품에도 문의 가능)
        productReader.getProduct(productId);
        // 3. 작성
        return inquiryAppender.write(memberId, productId, content, secret);
    }
}
