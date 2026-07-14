package com.commerce.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.commerce.member.entity.MemberStatus;
import com.commerce.member.entity.WithdrawalReason;
import com.commerce.member.exception.DuplicateEmailException;
import com.commerce.member.info.MemberInfo;
import com.commerce.member.service.MemberAppender;
import com.commerce.member.service.MemberReader;
import com.commerce.member.service.MemberRemover;
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
import org.springframework.test.context.TestConstructor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * member 도메인의 영속 이음새를 실 PostgreSQL로 검증한다.
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
@Import({MemberAppender.class, MemberReader.class, MemberRemover.class})
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class MemberPersistenceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

    private final MemberAppender memberAppender;
    private final MemberReader memberReader;
    private final MemberRemover memberRemover;
    private final TestEntityManager em;

    MemberPersistenceTest(
            MemberAppender memberAppender,
            MemberReader memberReader,
            MemberRemover memberRemover,
            TestEntityManager em) {
        this.memberAppender = memberAppender;
        this.memberReader = memberReader;
        this.memberRemover = memberRemover;
        this.em = em;
    }

    @Test
    @DisplayName("가입 후 조회 왕복 — 컨버터·파생 쿼리·validate 스키마 정합")
    void registerThenGetMember() {
        UUID id = memberAppender.register("user@example.com", "홍길동");
        em.flush();
        em.clear();

        MemberInfo info = memberReader.getMember(id);

        assertThat(info.email()).isEqualTo("user@example.com");
        assertThat(info.name()).isEqualTo("홍길동");
        assertThat(info.status()).isEqualTo(MemberStatus.ACTIVE);
    }

    @Test
    @DisplayName("활성 회원 사이 이메일 중복은 거부된다")
    void duplicateActiveEmailRejected() {
        memberAppender.register("dup@example.com", "회원1");
        em.flush();

        assertThatThrownBy(() -> memberAppender.register("dup@example.com", "회원2"))
                .isInstanceOf(DuplicateEmailException.class);
    }

    @Test
    @DisplayName("탈퇴 후 같은 이메일 재가입은 허용된다")
    void reregisterAfterWithdrawalAllowed() {
        UUID first = memberAppender.register("re@example.com", "회원1");
        em.flush();
        memberRemover.delete(first, WithdrawalReason.NO_LONGER_USED);
        em.flush();
        em.clear();

        UUID second = memberAppender.register("re@example.com", "회원2");

        assertThat(second).isNotEqualTo(first);
    }
}
