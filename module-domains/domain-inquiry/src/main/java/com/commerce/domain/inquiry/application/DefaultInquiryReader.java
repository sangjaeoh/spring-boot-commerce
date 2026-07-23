package com.commerce.domain.inquiry.application;

import com.commerce.domain.inquiry.application.info.InquiryInfo;
import com.commerce.domain.inquiry.application.provided.InquiryReader;
import com.commerce.domain.inquiry.application.required.InquiryRepository;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link InquiryReader}의 기본 구현이다. */
@Service
class DefaultInquiryReader implements InquiryReader {

    private final InquiryRepository inquiryRepository;

    DefaultInquiryReader(InquiryRepository inquiryRepository) {
        this.inquiryRepository = inquiryRepository;
    }

    @Transactional(readOnly = true)
    @Override
    public Page<InquiryInfo> getProductPage(UUID productId, Pageable pageable) {
        return inquiryRepository
                .findByProductIdOrderByIdDesc(productId, pageable)
                .map(InquiryInfo::from);
    }
}
