package com.commerce.member.repository;

import com.commerce.member.entity.MemberAddress;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberAddressRepository extends JpaRepository<MemberAddress, UUID> {

    /** 회원의 배송지를 기본 우선·최신 등록순으로 조회한다. */
    // UUIDv7이 시간순이라 id desc가 최신 등록순이다.
    List<MemberAddress> findByMemberIdOrderByDefaultAddressDescIdDesc(UUID memberId);

    /** 회원 스코프로 배송지 단건을 조회한다. 타인 배송지는 미존재와 같다. */
    Optional<MemberAddress> findByIdAndMemberId(UUID id, UUID memberId);

    /** 회원의 배송지 개수를 센다. */
    long countByMemberId(UUID memberId);

    /** 회원의 기본 배송지 지정을 일괄 해제한다. */
    // 기본 이전은 해제·지정이 한 불변식이라 벌크 UPDATE로 선행 해제한다.
    @Modifying
    @Query(
            "update MemberAddress a set a.defaultAddress = false where a.memberId = :memberId and a.defaultAddress = true")
    void releaseDefaultByMemberId(@Param("memberId") UUID memberId);
}
