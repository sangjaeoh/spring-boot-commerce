package com.commerce.inquiry.application;

import com.commerce.inquiry.application.provided.InquiryModifier;
import com.commerce.inquiry.application.required.InquiryRepository;
import com.commerce.inquiry.domain.InquiryErrorCode;
import com.commerce.inquiry.domain.InquiryNotFoundException;
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
