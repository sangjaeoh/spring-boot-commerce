package com.commerce.domain.member.application.provided;

import com.commerce.domain.member.application.info.MemberInfo;
import com.commerce.domain.member.domain.exception.InvalidEmailException;
import com.commerce.domain.member.domain.exception.MemberNotFoundException;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** 회원 조회를 담당하는 서비스다. */
public interface MemberReader {

    /**
     * 활성 회원을 조회한다. 정지 회원·정지 사유를 포함한다.
     *
     * @throws MemberNotFoundException 활성 회원이 없으면
     */
    MemberInfo getMember(UUID memberId);

    /**
     * 이메일 정확 일치로 활성 회원을 조회한다. 정지 회원·정지 사유를 포함한다.
     *
     * @throws MemberNotFoundException 해당 이메일의 활성 회원이 없으면
     * @throws InvalidEmailException 이메일 형식이 올바르지 않으면
     */
    MemberInfo getMemberByEmail(String email);

    /** 회원 ID 집합의 활성 회원들을 벌크 조회한다. 탈퇴 회원은 결과에서 빠진다. */
    List<MemberInfo> getMembers(Collection<UUID> memberIds);
}
