package com.commerce.architecture.bypass.application;

import com.commerce.member.domain.Member;

/**
 * 도메인 모듈 밖 {@code ...application} 패키지에서 생 엔티티를 참조하는 위반 표본이다.
 * {@code ArchitectureTest}가 엔티티 접근 면제의 패키지명 우회가 잡히는지 검증하는 데 쓴다.
 * 프로덕션 클래스 그래프({@code DO_NOT_INCLUDE_TESTS})에는 포함되지 않는다.
 */
public class EntityReferencingFixture {

    private final Member member;

    public EntityReferencingFixture(Member member) {
        this.member = member;
    }

    public Member member() {
        return member;
    }
}
