package com.commerce.inquiry.service;

import com.commerce.inquiry.entity.Inquiry;
import com.commerce.inquiry.exception.InvalidInquiryException;
import com.commerce.inquiry.repository.InquiryRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 문의 작성을 담당하는 서비스다. */
@Service
public class InquiryAppender {

    private final InquiryRepository inquiryRepository;

    public InquiryAppender(InquiryRepository inquiryRepository) {
        this.inquiryRepository = inquiryRepository;
    }

    /**
     * 문의를 쓰고 문의 ID를 반환한다.
     *
     * @throws InvalidInquiryException 본문이 허용 범위를 벗어나면
     */
    @Transactional
    public UUID write(UUID memberId, UUID productId, String content, boolean secret) {
        return inquiryRepository
                .save(Inquiry.create(memberId, productId, content, secret))
                .getId();
    }
}
