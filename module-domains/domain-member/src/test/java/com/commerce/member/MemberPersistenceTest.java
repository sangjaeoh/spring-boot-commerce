package com.commerce.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.commerce.member.entity.Member;
import com.commerce.member.entity.MemberRole;
import com.commerce.member.entity.MemberStatus;
import com.commerce.member.entity.SuspensionReason;
import com.commerce.member.entity.WithdrawalReason;
import com.commerce.member.exception.DuplicateEmailException;
import com.commerce.member.exception.InvalidCredentialsException;
import com.commerce.member.exception.InvalidPasswordException;
import com.commerce.member.info.MemberInfo;
import com.commerce.member.service.MemberAppender;
import com.commerce.member.service.MemberCredentialValidator;
import com.commerce.member.service.MemberModifier;
import com.commerce.member.service.MemberReader;
import com.commerce.member.service.MemberRemover;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.TestConstructor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * member 도메인의 영속 이음새를 실 PostgreSQL로 검증하는 테스트다.
 *
 * <p>컨버터 파생 쿼리, {@code ddl-auto=validate}와 Flyway 스키마 정합, 활성 이메일 부분 유니크를 확인한다.
 */
@DataJpaTest(
        properties = {
            "spring.jpa.hibernate.ddl-auto=validate",
            "spring.flyway.enabled=true",
            "spring.flyway.locations=classpath:db/migration/member",
            "spring.flyway.schemas=member",
            "spring.flyway.default-schema=member"
        })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({
    MemberAppender.class,
    MemberReader.class,
    MemberRemover.class,
    MemberModifier.class,
    MemberCredentialValidator.class
})
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class MemberPersistenceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

    private static final String PASSWORD = "password-123!";

    private final MemberAppender memberAppender;
    private final MemberReader memberReader;
    private final MemberRemover memberRemover;
    private final MemberModifier memberModifier;
    private final MemberCredentialValidator memberCredentialValidator;
    private final TestEntityManager em;

    MemberPersistenceTest(
            MemberAppender memberAppender,
            MemberReader memberReader,
            MemberRemover memberRemover,
            MemberModifier memberModifier,
            MemberCredentialValidator memberCredentialValidator,
            TestEntityManager em) {
        this.memberAppender = memberAppender;
        this.memberReader = memberReader;
        this.memberRemover = memberRemover;
        this.memberModifier = memberModifier;
        this.memberCredentialValidator = memberCredentialValidator;
        this.em = em;
    }

    @Test
    @DisplayName("가입 후 조회 왕복 — 컨버터·파생 쿼리·validate 스키마 정합")
    void registerThenGetMember() {
        UUID id = memberAppender.register("user@example.com", "홍길동", PASSWORD);
        em.flush();
        em.clear();

        MemberInfo info = memberReader.getMember(id);

        assertThat(info.email()).isEqualTo("user@example.com");
        assertThat(info.name()).isEqualTo("홍길동");
        assertThat(info.role()).isEqualTo(MemberRole.BUYER);
        assertThat(info.status()).isEqualTo(MemberStatus.ACTIVE);
    }

    @Test
    @DisplayName("관리자 가입은 ADMIN 역할로 저장된다")
    void registerAdminPersistsAdminRole() {
        UUID id = memberAppender.registerAdmin("admin@example.com", "관리자", PASSWORD);
        em.flush();
        em.clear();

        assertThat(memberReader.getMember(id).role()).isEqualTo(MemberRole.ADMIN);
    }

    @Test
    @DisplayName("활성 회원 사이 이메일 중복은 거부된다")
    void duplicateActiveEmailRejected() {
        memberAppender.register("dup@example.com", "회원1", PASSWORD);
        em.flush();

        assertThatThrownBy(() -> memberAppender.register("dup@example.com", "회원2", PASSWORD))
                .isInstanceOf(DuplicateEmailException.class);
    }

    @Test
    @DisplayName("탈퇴 후 같은 이메일 재가입은 허용된다")
    void reregisterAfterWithdrawalAllowed() {
        UUID first = memberAppender.register("re@example.com", "회원1", PASSWORD);
        em.flush();
        memberRemover.delete(first, WithdrawalReason.NO_LONGER_USED);
        em.flush();
        em.clear();

        UUID second = memberAppender.register("re@example.com", "회원2", PASSWORD);

        assertThat(second).isNotEqualTo(first);
    }

    @Test
    @DisplayName("패스워드는 평문이 아니라 bcrypt 해시로 저장된다")
    void passwordStoredAsBcryptHashNotPlaintext() {
        UUID id = memberAppender.register("hash@example.com", "홍길동", PASSWORD);
        em.flush();
        em.clear();

        String stored = Objects.requireNonNull(em.find(Member.class, id)).getPasswordHash();

        assertThat(stored).isNotEqualTo(PASSWORD).doesNotContain(PASSWORD).startsWith("$2");
        assertThat(new BCryptPasswordEncoder().matches(PASSWORD, stored)).isTrue();
    }

    @Test
    @DisplayName("정책(8자 미만·72바이트 초과)에 어긋난 패스워드 가입은 거부된다")
    void registerRejectsPolicyViolatingPassword() {
        assertThatThrownBy(() -> memberAppender.register("short@example.com", "홍길동", "a2345!7"))
                .isInstanceOf(InvalidPasswordException.class);
        assertThatThrownBy(() -> memberAppender.register("long@example.com", "홍길동", "한".repeat(25)))
                .isInstanceOf(InvalidPasswordException.class);
    }

    @Test
    @DisplayName("올바른 자격증명 검증은 검증된 회원을 반환하고, 정지 회원도 통과한다")
    void authenticateReturnsMemberAndPassesSuspended() {
        UUID id = memberAppender.register("login@example.com", "홍길동", PASSWORD);
        em.flush();

        assertThat(memberCredentialValidator
                        .authenticate("login@example.com", PASSWORD)
                        .id())
                .isEqualTo(id);

        memberModifier.suspend(id, SuspensionReason.POLICY_VIOLATION);
        em.flush();
        em.clear();

        assertThat(memberCredentialValidator
                        .authenticate("login@example.com", PASSWORD)
                        .id())
                .isEqualTo(id);
    }

    @Test
    @DisplayName("미존재·탈퇴·패스워드 불일치는 동일한 자격증명 거부로 응답한다")
    void authenticateRejectsMissingWithdrawnAndMismatchAlike() {
        UUID id = memberAppender.register("gone@example.com", "홍길동", PASSWORD);
        em.flush();

        assertThatThrownBy(() -> memberCredentialValidator.authenticate("gone@example.com", "wrong-password!"))
                .isInstanceOf(InvalidCredentialsException.class);
        assertThatThrownBy(() -> memberCredentialValidator.authenticate("nobody@example.com", PASSWORD))
                .isInstanceOf(InvalidCredentialsException.class);
        assertThatThrownBy(() -> memberCredentialValidator.authenticate("not-an-email", PASSWORD))
                .isInstanceOf(InvalidCredentialsException.class);

        memberRemover.delete(id, WithdrawalReason.NO_LONGER_USED);
        em.flush();
        em.clear();

        assertThatThrownBy(() -> memberCredentialValidator.authenticate("gone@example.com", PASSWORD))
                .isInstanceOf(InvalidCredentialsException.class);
    }
}
