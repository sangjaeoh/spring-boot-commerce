package com.commerce.api.presentation.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.commerce.api.presentation.v1.request.LoginRequest;
import com.commerce.api.presentation.v1.request.ProductRegistrationRequest;
import com.commerce.api.presentation.v1.response.LoginResponse;
import com.commerce.auth.token.AuthRole;
import com.commerce.auth.token.JwtTokenCodec;
import com.commerce.member.entity.MemberRole;
import com.commerce.member.info.MemberInfo;
import com.commerce.member.service.MemberCredentialValidator;
import java.util.List;
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
 * 관리자 계정 기동 시딩({@code AdminSeedConfig})을 검증한다. 하네스가 주입한 {@code auth.admin.*} 속성으로
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
    @DisplayName("시딩된 관리자로 로그인한 토큰은 ADMIN 역할을 싣고 관리자 오퍼레이션을 통과한다")
    void seededAdminLoginTokenPassesAdminGuard() throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(ADMIN_EMAIL, ADMIN_PASSWORD))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String accessToken = objectMapper.readValue(body, LoginResponse.class).accessToken();
        assertThat(jwtTokenCodec.verify(accessToken))
                .hasValueSatisfying(claims -> assertThat(claims.role()).isEqualTo(AuthRole.ADMIN));

        ProductRegistrationRequest request = new ProductRegistrationRequest("시딩검증셔츠", null, 10000L, List.of(), 1);
        mvc.perform(post("/api/v1/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.productId").exists());
    }
}
