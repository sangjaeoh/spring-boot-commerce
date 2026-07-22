package com.commerce.member.application.provided;

import com.commerce.member.domain.MemberAddressNotFoundException;
import java.util.UUID;

/** 회원 배송지 삭제를 담당하는 서비스다. */
public interface MemberAddressRemover {

    /**
     * 본인 배송지를 물리 삭제한다. 주문은 배송지 스냅샷을 보관하므로 이력 참조가 없다. 기본 배송지도 삭제할 수
     * 있으며 다른 배송지를 자동 승격하지 않는다.
     *
     * @throws MemberAddressNotFoundException 배송지가 없거나 타인 소유면
     */
    void purge(UUID addressId, UUID memberId);
}
