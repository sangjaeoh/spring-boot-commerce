package com.commerce.member.application;

import com.commerce.member.application.required.MemberAddressRepository;
import com.commerce.member.domain.MemberAddress;
import com.commerce.member.domain.MemberAddressNotFoundException;
import com.commerce.member.domain.MemberErrorCode;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 회원 배송지 삭제를 담당하는 서비스다. */
@Service
public class MemberAddressRemover {

    private final MemberAddressRepository memberAddressRepository;

    public MemberAddressRemover(MemberAddressRepository memberAddressRepository) {
        this.memberAddressRepository = memberAddressRepository;
    }

    /**
     * 본인 배송지를 물리 삭제한다. 주문은 배송지 스냅샷을 보관하므로 이력 참조가 없다. 기본 배송지도 삭제할 수
     * 있으며 다른 배송지를 자동 승격하지 않는다.
     *
     * @throws MemberAddressNotFoundException 배송지가 없거나 타인 소유면
     */
    @Transactional
    public void purge(UUID addressId, UUID memberId) {
        MemberAddress address = memberAddressRepository
                .findByIdAndMemberId(addressId, memberId)
                .orElseThrow(() -> new MemberAddressNotFoundException(MemberErrorCode.ADDRESS_NOT_FOUND));
        memberAddressRepository.delete(address);
    }
}
