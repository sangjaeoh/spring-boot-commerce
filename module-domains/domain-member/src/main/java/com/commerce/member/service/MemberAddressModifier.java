package com.commerce.member.service;

import com.commerce.member.entity.MemberAddress;
import com.commerce.member.exception.MemberAddressNotFoundException;
import com.commerce.member.exception.MemberErrorCode;
import com.commerce.member.repository.MemberAddressRepository;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 회원 배송지 수정·기본 지정을 담당하는 서비스다. */
@Service
public class MemberAddressModifier {

    private final MemberAddressRepository memberAddressRepository;

    public MemberAddressModifier(MemberAddressRepository memberAddressRepository) {
        this.memberAddressRepository = memberAddressRepository;
    }

    /**
     * 본인 배송지의 정보를 수정한다. 기본 여부는 바꾸지 않는다.
     *
     * @throws MemberAddressNotFoundException 배송지가 없거나 타인 소유면
     */
    @Transactional
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

    /**
     * 본인 배송지를 기본 배송지로 지정한다. 기존 기본 지정은 같은 트랜잭션에서 해제돼 기본은 항상 하나다.
     *
     * @throws MemberAddressNotFoundException 배송지가 없거나 타인 소유면
     */
    @Transactional
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
