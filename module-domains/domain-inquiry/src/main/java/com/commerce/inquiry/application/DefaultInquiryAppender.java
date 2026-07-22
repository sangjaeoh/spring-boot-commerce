package com.commerce.inquiry.application;

import com.commerce.inquiry.application.provided.InquiryAppender;
import com.commerce.inquiry.application.required.InquiryRepository;
import com.commerce.inquiry.domain.Inquiry;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link InquiryAppender}의 기본 구현이다. */
@Service
class DefaultInquiryAppender implements InquiryAppender {

    private final InquiryRepository inquiryRepository;

    DefaultInquiryAppender(InquiryRepository inquiryRepository) {
        this.inquiryRepository = inquiryRepository;
    }

    @Transactional
    @Override
    public UUID write(UUID memberId, UUID productId, String content, boolean secret) {
        return inquiryRepository
                .save(Inquiry.create(memberId, productId, content, secret))
                .getId();
    }
}
