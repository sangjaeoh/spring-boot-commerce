package com.commerce.member.application.provided;

import com.commerce.member.domain.MemberAddressNotFoundException;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 회원 배송지 수정·기본 지정을 담당하는 서비스다. */
public interface MemberAddressModifier {

    /**
     * 본인 배송지의 정보를 수정한다. 기본 여부는 바꾸지 않는다.
     *
     * @throws MemberAddressNotFoundException 배송지가 없거나 타인 소유면
     */
    void revise(
            UUID addressId,
            UUID memberId,
            String recipientName,
            String zipCode,
            String roadAddress,
            @Nullable String detailAddress,
            String phone);

    /**
     * 본인 배송지를 기본 배송지로 지정한다. 기존 기본 지정은 같은 트랜잭션에서 해제돼 기본은 항상 하나다.
     *
     * @throws MemberAddressNotFoundException 배송지가 없거나 타인 소유면
     */
    void designateDefault(UUID addressId, UUID memberId);
}
