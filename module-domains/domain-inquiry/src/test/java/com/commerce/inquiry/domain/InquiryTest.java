package com.commerce.inquiry.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InquiryTest {

    private static final UUID MEMBER_ID = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.randomUUID();

    @Test
    @DisplayName("범위 안 본문으로 문의가 생성되고 비밀글 여부·미답변 상태를 담는다")
    void createSetsFields() {
        Inquiry inquiry = Inquiry.create(MEMBER_ID, PRODUCT_ID, "배송은 얼마나 걸리나요?", true);

        assertThat(inquiry.getId()).isNotNull();
        assertThat(inquiry.getMemberId()).isEqualTo(MEMBER_ID);
        assertThat(inquiry.getProductId()).isEqualTo(PRODUCT_ID);
        assertThat(inquiry.getContent()).isEqualTo("배송은 얼마나 걸리나요?");
        assertThat(inquiry.isSecret()).isTrue();
        assertThat(inquiry.getAnswer()).isNull();
    }

    @Test
    @DisplayName("본문이 공백이거나 1000자를 넘으면 생성이 거부된다")
    void createRejectsBlankOrOverlongContent() {
        assertThatThrownBy(() -> Inquiry.create(MEMBER_ID, PRODUCT_ID, "   ", false))
                .isInstanceOf(InvalidInquiryException.class)
                .extracting("errorCode")
                .isEqualTo(InquiryErrorCode.INVALID_CONTENT);
        assertThatThrownBy(() -> Inquiry.create(MEMBER_ID, PRODUCT_ID, "가".repeat(1001), false))
                .isInstanceOf(InvalidInquiryException.class)
                .extracting("errorCode")
                .isEqualTo(InquiryErrorCode.INVALID_CONTENT);
    }

    @Test
    @DisplayName("답변을 달면 실리고, 재답변은 덮어쓴다")
    void answerSetsAndOverwrites() {
        Inquiry inquiry = Inquiry.create(MEMBER_ID, PRODUCT_ID, "재입고 예정이 있나요?", false);

        inquiry.answer("다음 주 재입고 예정입니다.");
        assertThat(inquiry.getAnswer()).isEqualTo("다음 주 재입고 예정입니다.");

        inquiry.answer("이번 주 금요일 재입고됩니다.");
        assertThat(inquiry.getAnswer()).isEqualTo("이번 주 금요일 재입고됩니다.");
    }

    @Test
    @DisplayName("답변 본문도 공백·1000자 초과면 거부된다")
    void answerRejectsBlankOrOverlongContent() {
        Inquiry inquiry = Inquiry.create(MEMBER_ID, PRODUCT_ID, "문의 본문", false);

        assertThatThrownBy(() -> inquiry.answer("   "))
                .isInstanceOf(InvalidInquiryException.class)
                .extracting("errorCode")
                .isEqualTo(InquiryErrorCode.INVALID_CONTENT);
        assertThatThrownBy(() -> inquiry.answer("가".repeat(1001)))
                .isInstanceOf(InvalidInquiryException.class)
                .extracting("errorCode")
                .isEqualTo(InquiryErrorCode.INVALID_CONTENT);
    }
}
