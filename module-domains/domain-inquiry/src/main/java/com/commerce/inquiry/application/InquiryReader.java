package com.commerce.inquiry.application;

import com.commerce.inquiry.application.info.InquiryInfo;
import com.commerce.inquiry.application.required.InquiryRepository;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 문의 조회를 담당하는 서비스다. */
@Service
public class InquiryReader {

    private final InquiryRepository inquiryRepository;

    public InquiryReader(InquiryRepository inquiryRepository) {
        this.inquiryRepository = inquiryRepository;
    }

    /** 상품의 문의 페이지를 최신 문의 우선으로 조회한다. */
    @Transactional(readOnly = true)
    public Page<InquiryInfo> getProductPage(UUID productId, Pageable pageable) {
        return inquiryRepository
                .findByProductIdOrderByIdDesc(productId, pageable)
                .map(InquiryInfo::from);
    }
}
