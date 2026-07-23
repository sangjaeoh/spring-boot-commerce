package com.commerce.domain.member.application.provided;

import com.commerce.domain.member.domain.exception.MemberAddressLimitException;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 회원 배송지 등록을 담당하는 서비스다. */
public interface MemberAddressAppender {

    /**
     * 회원 배송지를 등록하고 새 배송지 ID를 반환한다. 첫 배송지는 자동으로 기본 배송지가 된다.
     *
     * @throws MemberAddressLimitException 회원당 한도(10개)를 넘으면
     */
    UUID add(
            UUID memberId,
            String recipientName,
            String zipCode,
            String roadAddress,
            @Nullable String detailAddress,
            String phone);
}
