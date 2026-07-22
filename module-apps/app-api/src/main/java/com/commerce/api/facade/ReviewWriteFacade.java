package com.commerce.api.facade;

import com.commerce.api.exception.ApiErrorCode;
import com.commerce.api.exception.ApiException;
import com.commerce.order.service.OrderReader;
import com.commerce.review.service.ReviewAppender;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** 리뷰 작성의 구매확정 자격을 조율하는 파사드다. */
@Component
public class ReviewWriteFacade {

    private final OrderReader orderReader;
    private final ReviewAppender reviewAppender;

    public ReviewWriteFacade(OrderReader orderReader, ReviewAppender reviewAppender) {
        this.orderReader = orderReader;
        this.reviewAppender = reviewAppender;
    }

    /**
     * 구매확정(배송 완료) 자격을 확인하고 리뷰를 쓴 뒤 리뷰 ID를 반환한다.
     *
     * @throws ApiException 회원에게 해당 상품의 배송 완료 주문이 없으면
     */
    public UUID write(UUID memberId, UUID productId, int rating, String content) {
        // 1. 자격 확인
        if (!orderReader.hasDeliveredProduct(memberId, productId)) {
            throw new ApiException(ApiErrorCode.REVIEW_NOT_ELIGIBLE);
        }
        // 2. 작성
        return reviewAppender.write(memberId, productId, rating, content);
    }
}
