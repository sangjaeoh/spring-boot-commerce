package com.commerce.inquiry.application.provided;

import com.commerce.inquiry.domain.InvalidInquiryException;
import java.util.UUID;

/** 문의 작성을 담당하는 서비스다. */
public interface InquiryAppender {

    /**
     * 문의를 쓰고 문의 ID를 반환한다.
     *
     * @throws InvalidInquiryException 본문이 허용 범위를 벗어나면
     */
    UUID write(UUID memberId, UUID productId, String content, boolean secret);
}
