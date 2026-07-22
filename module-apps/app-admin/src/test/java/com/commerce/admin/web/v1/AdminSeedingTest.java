package com.commerce.admin.web.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.commerce.admin.web.v1.admin.product.request.ProductRegistrationRequest;
import com.commerce.auth.token.JwtTokenCodec;
import com.commerce.member.application.info.MemberInfo;
import com.commerce.member.application.provided.MemberCredentialValidator;
import com.commerce.member.domain.MemberRole;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/**
 * 관리자 계정 기동 시딩({@code AdminSeedConfig})을 검증하는 테스트다. 하네스가 주입한 {@code auth.admin.*} 속성으로
 * 컨텍스트 기동 시 시딩이 이미 실행된 상태를 전제한다.
 */
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class AdminSeedingTest extends WebIntegrationTest {

    private final MockMvc mvc;
    private final ObjectMapper objectMapper;
    private final MemberCredentialValidator memberCredentialValidator;
    private final JwtTokenCodec jwtTokenCodec;
    private final ApplicationRunner adminAccountSeeder;

    AdminSeedingTest(
            MockMvc mvc,
            ObjectMapper objectMapper,
            MemberCredentialValidator memberCredentialValidator,
            JwtTokenCodec jwtTokenCodec,
            ApplicationRunner adminAccountSeeder) {
        this.mvc = mvc;
        this.objectMapper = objectMapper;
        this.memberCredentialValidator = memberCredentialValidator;
        this.jwtTokenCodec = jwtTokenCodec;
        this.adminAccountSeeder = adminAccountSeeder;
    }

    @Test
    @DisplayName("기동 시딩이 설정된 자격증명으로 ADMIN 역할 회원을 만든다")
    void seedingCreatesAdminRoleMember() {
        MemberInfo admin = memberCredentialValidator.authenticate(ADMIN_EMAIL, ADMIN_PASSWORD);

        assertThat(admin.role()).isEqualTo(MemberRole.ADMIN);
    }

    @Test
    @DisplayName("시딩을 재실행해도 실패 없이 같은 계정이 유지된다(재기동 멱등)")
    void seedingIsIdempotentAcrossReruns() throws Exception {
        adminAccountSeeder.run(new DefaultApplicationArguments());

        assertThat(memberCredentialValidator
                        .authenticate(ADMIN_EMAIL, ADMIN_PASSWORD)
                        .role())
                .isEqualTo(MemberRole.ADMIN);
    }

    @Test
    @DisplayName("시딩된 관리자 자격증명의 ADMIN 역할 토큰이 관리자 오퍼레이션을 통과한다")
    // 로그인 HTTP 표면은 app-api 소유라 자격증명 검증·발급 코덱으로 토큰을 만든다(로그인 왕복은 app-api가 검증).
    void seededAdminTokenPassesAdminGuard() throws Exception {
        UUID adminId = memberCredentialValidator
                .authenticate(ADMIN_EMAIL, ADMIN_PASSWORD)
                .id();
        String accessToken = jwtTokenCodec.issue(adminId.toString(), Map.of("role", "ADMIN"));
        assertThat(jwtTokenCodec.verify(accessToken))
                .hasValueSatisfying(
                        claims -> assertThat(claims.claims().get("role")).isEqualTo("ADMIN"));

        ProductRegistrationRequest request = new ProductRegistrationRequest("시딩검증셔츠", null, null, 10000L, List.of(), 1);
        mvc.perform(post("/api/v1/admin/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.productId").exists());
    }
}
