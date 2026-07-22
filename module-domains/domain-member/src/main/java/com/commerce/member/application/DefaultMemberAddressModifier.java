package com.commerce.member.application;

import com.commerce.member.application.provided.MemberAddressModifier;
import com.commerce.member.application.required.MemberAddressRepository;
import com.commerce.member.domain.MemberAddress;
import com.commerce.member.domain.MemberAddressNotFoundException;
import com.commerce.member.domain.MemberErrorCode;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link MemberAddressModifier}의 기본 구현이다. */
@Service
class DefaultMemberAddressModifier implements MemberAddressModifier {

    private final MemberAddressRepository memberAddressRepository;

    DefaultMemberAddressModifier(MemberAddressRepository memberAddressRepository) {
        this.memberAddressRepository = memberAddressRepository;
    }

    @Transactional
    @Override
    public void revise(
            UUID addressId,
            UUID memberId,
            String recipientName,
            String zipCode,
            String roadAddress,
            @Nullable String detailAddress,
            String phone) {
        find(addressId, memberId).revise(recipientName, zipCode, roadAddress, detailAddress, phone);
    }

    @Transactional
    @Override
    public void designateDefault(UUID addressId, UUID memberId) {
        MemberAddress address = find(addressId, memberId);
        // 벌크 해제는 영속성 컨텍스트를 우회하므로, 이미 기본인 대상은 스냅샷과 값이 같아 재지정이
        // flush되지 않는다. 조기 반환으로 해제-미지정 상태를 막는다.
        if (address.isDefaultAddress()) {
            return;
        }
        memberAddressRepository.releaseDefaultByMemberId(memberId);
        address.markDefault();
    }

    /** 본인 배송지를 찾고 없으면 거부한다. */
    private MemberAddress find(UUID addressId, UUID memberId) {
        return memberAddressRepository
                .findByIdAndMemberId(addressId, memberId)
                .orElseThrow(() -> new MemberAddressNotFoundException(MemberErrorCode.ADDRESS_NOT_FOUND));
    }
}
