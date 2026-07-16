package com.commerce.member.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.commerce.member.exception.MemberStatusException;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MemberTest {

    private static final Instant NOW = Instant.parse("2025-06-15T00:00:00Z");

    private Member activeMember() {
        return Member.create(Email.of("user@example.com"), "홍길동", "{hashed}password", MemberRole.BUYER);
    }

    @Test
    @DisplayName("생성 시 ACTIVE이고 정지사유·삭제시각이 없다")
    void createsActiveWithoutSuspensionOrDeletion() {
        Member member = activeMember();
        assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(member.getSuspensionReason()).isNull();
        assertThat(member.getDeletedAt()).isNull();
        assertThat(member.getId()).isNotNull();
    }

    @Test
    @DisplayName("생성 시 받은 역할을 보존한다")
    void createKeepsGivenRole() {
        Member admin = Member.create(Email.of("admin@example.com"), "관리자", "{hashed}password", MemberRole.ADMIN);
        assertThat(activeMember().getRole()).isEqualTo(MemberRole.BUYER);
        assertThat(admin.getRole()).isEqualTo(MemberRole.ADMIN);
    }

    @Test
    @DisplayName("정지하면 SUSPENDED이고 사유가 기록된다")
    void suspendSetsStatusAndReason() {
        Member member = activeMember();
        member.suspend(SuspensionReason.FRAUD_SUSPECTED);
        assertThat(member.getStatus()).isEqualTo(MemberStatus.SUSPENDED);
        assertThat(member.getSuspensionReason()).isEqualTo(SuspensionReason.FRAUD_SUSPECTED);
    }

    @Test
    @DisplayName("이미 정지된 회원은 다시 정지할 수 없다")
    void cannotSuspendAlreadySuspended() {
        Member member = activeMember();
        member.suspend(SuspensionReason.CS_MANUAL);
        assertThatThrownBy(() -> member.suspend(SuspensionReason.POLICY_VIOLATION))
                .isInstanceOf(MemberStatusException.class);
    }

    @Test
    @DisplayName("정지 해제하면 ACTIVE이고 사유가 지워진다")
    void reinstateClearsReason() {
        Member member = activeMember();
        member.suspend(SuspensionReason.CS_MANUAL);
        member.reinstate();
        assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(member.getSuspensionReason()).isNull();
    }

    @Test
    @DisplayName("정지되지 않은 회원은 해제할 수 없다")
    void cannotReinstateActive() {
        assertThatThrownBy(() -> activeMember().reinstate()).isInstanceOf(MemberStatusException.class);
    }

    @Test
    @DisplayName("이름을 바꿔도 이메일은 불변이다")
    void renameKeepsEmail() {
        Member member = activeMember();
        member.rename("김철수");
        assertThat(member.getName()).isEqualTo("김철수");
        assertThat(member.getEmail()).isEqualTo(Email.of("user@example.com"));
    }

    @Test
    @DisplayName("탈퇴하면 삭제시각과 사유가 기록된다")
    void deleteSetsDeletedAtAndReason() {
        Member member = activeMember();
        member.delete(WithdrawalReason.NO_LONGER_USED, NOW);
        assertThat(member.getDeletedAt()).isNotNull();
        assertThat(member.getWithdrawalReason()).isEqualTo(WithdrawalReason.NO_LONGER_USED);
    }
}
