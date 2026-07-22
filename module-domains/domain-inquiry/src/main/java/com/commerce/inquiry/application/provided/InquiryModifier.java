package com.commerce.inquiry.application.provided;

import com.commerce.inquiry.domain.InquiryNotFoundException;
import com.commerce.inquiry.domain.InvalidInquiryException;
import java.util.UUID;

/** 문의 답변을 담당하는 서비스다. */
public interface InquiryModifier {

    /**
     * 문의에 답변을 단다. 이미 답변이 있으면 덮어쓴다.
     *
     * @throws InquiryNotFoundException 문의가 없으면
     * @throws InvalidInquiryException 답변 본문이 허용 범위를 벗어나면
     */
    void answer(UUID inquiryId, String content);
}
