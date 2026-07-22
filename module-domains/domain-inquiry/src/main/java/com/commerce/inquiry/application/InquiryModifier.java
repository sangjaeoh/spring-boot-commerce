package com.commerce.inquiry.application;

import com.commerce.inquiry.application.required.InquiryRepository;
import com.commerce.inquiry.domain.InquiryErrorCode;
import com.commerce.inquiry.domain.InquiryNotFoundException;
import com.commerce.inquiry.domain.InvalidInquiryException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 문의 답변을 담당하는 서비스다. */
@Service
public class InquiryModifier {

    private final InquiryRepository inquiryRepository;

    public InquiryModifier(InquiryRepository inquiryRepository) {
        this.inquiryRepository = inquiryRepository;
    }

    /**
     * 문의에 답변을 단다. 이미 답변이 있으면 덮어쓴다.
     *
     * @throws InquiryNotFoundException 문의가 없으면
     * @throws InvalidInquiryException 답변 본문이 허용 범위를 벗어나면
     */
    @Transactional
    public void answer(UUID inquiryId, String content) {
        inquiryRepository
                .findById(inquiryId)
                .orElseThrow(() -> new InquiryNotFoundException(InquiryErrorCode.INQUIRY_NOT_FOUND))
                .answer(content);
    }
}
