package com.commerce.member.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.commerce.member.application.required.MemberRepository;
import com.commerce.member.domain.Email;
import com.commerce.member.domain.InvalidCredentialsException;
import com.commerce.member.domain.Member;
import com.commerce.member.domain.MemberRole;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 미존재·탈퇴 경로의 타이밍 등화를 구조적으로 검증하는 테스트다.
 *
 * <p>실측 타이밍이 아니라 인코더 호출·거부 불변으로 확인한다. 미존재·탈퇴는 이 계층에서 모두 저장소 빈
 * {@code Optional}로 나타난다(탈퇴 실배선은 {@link com.commerce.member.MemberPersistenceTest MemberPersistenceTest}가 커버).
 */
class MemberCredentialValidatorTest {

    private static final String RAW_PASSWORD = "password-123!";

    private final MemberRepository memberRepository = mock(MemberRepository.class);
    private final RecordingPasswordEncoder passwordEncoder = new RecordingPasswordEncoder();
    private final MemberCredentialValidator validator =
            new MemberCredentialValidator(memberRepository, passwordEncoder);

    @Test
    @DisplayName("미존재 계정도 고정 더미 해시로 bcrypt를 한 번 태우고 거부한다")
    void missingAccountRunsEncoderAgainstDummyHashThenRejects() {
        when(memberRepository.findByEmailAndDeletedAtIsNull(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> validator.authenticate("nobody@example.com", RAW_PASSWORD))
                .isInstanceOf(InvalidCredentialsException.class);

        assertThat(passwordEncoder.matchedHashes).containsExactly(passwordEncoder.dummyHash);
    }

    @Test
    @DisplayName("더미 비교가 우연히 일치해도 미존재 계정은 항상 거부한다")
    void missingAccountRejectedEvenWhenDummyMatches() {
        when(memberRepository.findByEmailAndDeletedAtIsNull(any())).thenReturn(Optional.empty());
        passwordEncoder.matchResult = true;

        assertThatThrownBy(() -> validator.authenticate("nobody@example.com", RAW_PASSWORD))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("존재 계정은 저장 해시로 검증하고 일치하면 통과한다")
    void existingAccountVerifiesAgainstStoredHashThenPasses() {
        Member member = Member.create(Email.of("user@example.com"), "홍길동", "$stored-hash$", MemberRole.BUYER);
        when(memberRepository.findByEmailAndDeletedAtIsNull(any())).thenReturn(Optional.of(member));
        passwordEncoder.matchResult = true;

        assertThat(validator.authenticate("user@example.com", RAW_PASSWORD).id())
                .isEqualTo(member.getId());
        assertThat(passwordEncoder.matchedHashes).containsExactly("$stored-hash$");
    }

    /** 비교에 쓰인 해시를 기록하고 일치 결과를 고정하는 {@link PasswordEncoder} 스텁이다. */
    private static final class RecordingPasswordEncoder implements PasswordEncoder {

        private final String dummyHash = "$dummy-equalization-hash$";
        private final List<@Nullable String> matchedHashes = new ArrayList<>();
        private boolean matchResult = false;

        @Override
        public String encode(@Nullable CharSequence rawPassword) {
            return dummyHash;
        }

        @Override
        public boolean matches(@Nullable CharSequence rawPassword, @Nullable String encodedPassword) {
            matchedHashes.add(encodedPassword);
            return matchResult;
        }
    }
}
