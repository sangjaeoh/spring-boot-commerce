package com.commerce.member.application.provided;

import com.commerce.member.application.info.MemberAddressInfo;
import java.util.List;
import java.util.UUID;

/** 회원 배송지 조회를 담당하는 서비스다. */
public interface MemberAddressReader {

    /** 회원의 배송지 목록을 기본 우선·최신 등록순으로 조회한다. 없으면 빈 목록이다. */
    List<MemberAddressInfo> getAddresses(UUID memberId);
}
