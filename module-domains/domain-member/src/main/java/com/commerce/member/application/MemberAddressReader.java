package com.commerce.member.application;

import com.commerce.member.application.info.MemberAddressInfo;
import com.commerce.member.application.required.MemberAddressRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 회원 배송지 조회를 담당하는 서비스다. */
@Service
public class MemberAddressReader {

    private final MemberAddressRepository memberAddressRepository;

    public MemberAddressReader(MemberAddressRepository memberAddressRepository) {
        this.memberAddressRepository = memberAddressRepository;
    }

    /** 회원의 배송지 목록을 기본 우선·최신 등록순으로 조회한다. 없으면 빈 목록이다. */
    @Transactional(readOnly = true)
    public List<MemberAddressInfo> getAddresses(UUID memberId) {
        return memberAddressRepository.findByMemberIdOrderByDefaultAddressDescIdDesc(memberId).stream()
                .map(MemberAddressInfo::from)
                .toList();
    }
}
