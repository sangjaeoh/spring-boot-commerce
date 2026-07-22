package com.commerce.member.application;

import com.commerce.member.application.provided.MemberAddressAppender;
import com.commerce.member.application.required.MemberAddressRepository;
import com.commerce.member.domain.MemberAddress;
import com.commerce.member.domain.MemberAddressLimitException;
import com.commerce.member.domain.MemberErrorCode;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link MemberAddressAppender}의 기본 구현이다. */
@Service
class DefaultMemberAddressAppender implements MemberAddressAppender {

    // 회원당 배송지 등록 한도.
    static final int MAX_ADDRESSES = 10;

    private final MemberAddressRepository memberAddressRepository;

    DefaultMemberAddressAppender(MemberAddressRepository memberAddressRepository) {
        this.memberAddressRepository = memberAddressRepository;
    }

    @Transactional
    @Override
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
