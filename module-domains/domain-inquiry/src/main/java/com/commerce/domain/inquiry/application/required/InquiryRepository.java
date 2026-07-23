package com.commerce.domain.inquiry.application.required;

import com.commerce.domain.inquiry.domain.Inquiry;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InquiryRepository extends JpaRepository<Inquiry, UUID> {

    // UUIDv7 ID가 생성 시각 순서라 ID 내림차순이 최신 문의 우선 정렬을 겸한다.
    Page<Inquiry> findByProductIdOrderByIdDesc(UUID productId, Pageable pageable);

    Page<Inquiry> findByAnswerIsNullOrderByIdDesc(Pageable pageable);

    Page<Inquiry> findByAnswerIsNotNullOrderByIdDesc(Pageable pageable);
}
