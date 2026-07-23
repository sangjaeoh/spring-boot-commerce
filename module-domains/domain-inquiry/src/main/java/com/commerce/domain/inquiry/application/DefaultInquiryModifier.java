package com.commerce.domain.inquiry.application;

import com.commerce.domain.inquiry.application.provided.InquiryModifier;
import com.commerce.domain.inquiry.application.required.InquiryRepository;
import com.commerce.domain.inquiry.domain.exception.InquiryErrorCode;
import com.commerce.domain.inquiry.domain.exception.InquiryNotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link InquiryModifier}의 기본 구현이다. */
@Service
class DefaultInquiryModifier implements InquiryModifier {

    private final InquiryRepository inquiryRepository;

    DefaultInquiryModifier(InquiryRepository inquiryRepository) {
        this.inquiryRepository = inquiryRepository;
    }

    @Transactional
    @Override
    public void answer(UUID inquiryId, String content) {
        inquiryRepository
                .findById(inquiryId)
                .orElseThrow(() -> new InquiryNotFoundException(InquiryErrorCode.INQUIRY_NOT_FOUND))
                .answer(content);
    }
}
