package com.commerce.member.application;

import com.commerce.member.application.provided.MemberAddressRemover;
import com.commerce.member.application.required.MemberAddressRepository;
import com.commerce.member.domain.MemberAddress;
import com.commerce.member.domain.MemberAddressNotFoundException;
import com.commerce.member.domain.MemberErrorCode;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link MemberAddressRemover}의 기본 구현이다. */
@Service
class DefaultMemberAddressRemover implements MemberAddressRemover {

    private final MemberAddressRepository memberAddressRepository;

    DefaultMemberAddressRemover(MemberAddressRepository memberAddressRepository) {
        this.memberAddressRepository = memberAddressRepository;
    }

    @Transactional
    @Override
    public void purge(UUID addressId, UUID memberId) {
        MemberAddress address = memberAddressRepository
                .findByIdAndMemberId(addressId, memberId)
                .orElseThrow(() -> new MemberAddressNotFoundException(MemberErrorCode.ADDRESS_NOT_FOUND));
        memberAddressRepository.delete(address);
    }
}
