package com.commerce.member.application;

import com.commerce.member.application.required.MemberAddressRepository;
import com.commerce.member.domain.MemberAddress;
import com.commerce.member.domain.MemberAddressLimitException;
import com.commerce.member.domain.MemberErrorCode;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 회원 배송지 등록을 담당하는 서비스다. */
@Service
public class MemberAddressAppender {

    // 회원당 배송지 등록 한도.
    static final int MAX_ADDRESSES = 10;

    private final MemberAddressRepository memberAddressRepository;

    public MemberAddressAppender(MemberAddressRepository memberAddressRepository) {
        this.memberAddressRepository = memberAddressRepository;
    }

    /**
     * 회원 배송지를 등록하고 새 배송지 ID를 반환한다. 첫 배송지는 자동으로 기본 배송지가 된다.
     *
     * @throws MemberAddressLimitException 회원당 한도(10개)를 넘으면
     */
    @Transactional
    public UUID add(
            UUID memberId,
            String recipientName,
            String zipCode,
            String roadAddress,
            @Nullable String detailAddress,
            String phone) {
        long count = memberAddressRepository.countByMemberId(memberId);
        if (count >= MAX_ADDRESSES) {
            throw new MemberAddressLimitException(MemberErrorCode.ADDRESS_LIMIT_EXCEEDED);
        }
        MemberAddress address =
                MemberAddress.create(memberId, recipientName, zipCode, roadAddress, detailAddress, phone, count == 0);
        return memberAddressRepository.save(address).getId();
    }
}
