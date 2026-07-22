package com.commerce.member.application;

import com.commerce.member.application.info.MemberAddressInfo;
import com.commerce.member.application.provided.MemberAddressReader;
import com.commerce.member.application.required.MemberAddressRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link MemberAddressReader}의 기본 구현이다. */
@Service
class DefaultMemberAddressReader implements MemberAddressReader {

    private final MemberAddressRepository memberAddressRepository;

    DefaultMemberAddressReader(MemberAddressRepository memberAddressRepository) {
        this.memberAddressRepository = memberAddressRepository;
    }

    @Transactional(readOnly = true)
    @Override
    public List<MemberAddressInfo> getAddresses(UUID memberId) {
        return memberAddressRepository.findByMemberIdOrderByDefaultAddressDescIdDesc(memberId).stream()
                .map(MemberAddressInfo::from)
                .toList();
    }
}
