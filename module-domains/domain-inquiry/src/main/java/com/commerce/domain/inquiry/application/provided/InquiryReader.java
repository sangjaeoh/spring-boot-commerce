package com.commerce.domain.inquiry.application.provided;

import com.commerce.domain.inquiry.application.info.InquiryInfo;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** 문의 조회를 담당하는 서비스다. */
public interface InquiryReader {

    /** 상품의 문의 페이지를 최신 문의 우선으로 조회한다. */
    Page<InquiryInfo> getProductPage(UUID productId, Pageable pageable);
}
