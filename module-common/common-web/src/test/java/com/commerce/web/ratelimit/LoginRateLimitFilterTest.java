package com.commerce.web.ratelimit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class LoginRateLimitFilterTest {

    private static final int MAX_ATTEMPTS = 3;
    private static final Duration WINDOW = Duration.ofMinutes(5);
    private static final RateLimitScope SCOPE = new RateLimitScope(
            "login:",
            "TOO_MANY_LOGIN_ATTEMPTS",
            "로그인 시도가 너무 많다. 잠시 후 다시 시도해 주세요.",
            "일시적으로 로그인을 처리할 수 없다. 잠시 후 다시 시도해 주세요.");

    private final WebApplicationContext context;

    LoginRateLimitFilterTest(WebApplicationContext context) {
        this.context = context;
    }

    private MockMvc mvcWith(LoginRateLimitStore store) {
        return MockMvcBuilders.webAppContextSetup(context)
                .addFilters(new LoginRateLimitFilter(store, SCOPE, MAX_ATTEMPTS, WINDOW))
                .build();
    }

    @Test
    @DisplayName("한도까지는 통과하고, 창 안에서 한도를 넘긴 시도는 problem+json 429로 거부된다")
    void rejectsWhenAttemptsExceedLimitWithinWindow() throws Exception {
        MockMvc mvc = mvcWith(new InMemoryLoginRateLimitStore(new AtomicLong()::get));

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            mvc.perform(post("/test/echo")).andExpect(status().isOk());
        }
        mvc.perform(post("/test/echo"))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string("Retry-After", "300"))
                .andExpect(jsonPath("$.code").value("TOO_MANY_LOGIN_ATTEMPTS"))
                .andExpect(jsonPath("$.status").value(429));
    }

    @Test
    @DisplayName("창이 경과하면 카운터가 초기화돼 다시 시도할 수 있다")
    void allowsAgainAfterWindowElapses() throws Exception {
        AtomicLong clock = new AtomicLong();
        MockMvc mvc = mvcWith(new InMemoryLoginRateLimitStore(clock::get));

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            mvc.perform(post("/test/echo")).andExpect(status().isOk());
        }
        mvc.perform(post("/test/echo")).andExpect(status().isTooManyRequests());

        clock.addAndGet(WINDOW.toMillis() + 1);

        mvc.perform(post("/test/echo")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("서로 다른 클라이언트 IP는 각자의 카운터를 가진다")
    void countsPerClientAddress() throws Exception {
        MockMvc mvc = mvcWith(new InMemoryLoginRateLimitStore(new AtomicLong()::get));

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            mvc.perform(fromAddress("10.0.0.1")).andExpect(status().isOk());
        }
        mvc.perform(fromAddress("10.0.0.1")).andExpect(status().isTooManyRequests());
        mvc.perform(fromAddress("10.0.0.2")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST가 아닌 요청은 시도로 세지 않고 한도 소진 후에도 통과한다")
    void nonPostRequestsBypassThrottle() throws Exception {
        MockMvc mvc = mvcWith(new InMemoryLoginRateLimitStore(new AtomicLong()::get));

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            mvc.perform(post("/test/echo")).andExpect(status().isOk());
        }
        mvc.perform(post("/test/echo")).andExpect(status().isTooManyRequests());

        // 같은 경로에 겹칠 수 있는 비-POST 표면(관리자 GET 검색 등)은 스로틀 대상이 아니다.
        mvc.perform(get("/test/echo")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("저장소에 접근할 수 없으면 시도를 통과시키지 않고 503으로 거부한다(fail-closed)")
    void rejectsWhenStoreUnavailable() throws Exception {
        LoginRateLimitStore failing = (key, window) -> {
            throw new IllegalStateException("store down");
        };
        MockMvc mvc = mvcWith(failing);

        mvc.perform(post("/test/echo"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_UNAVAILABLE"));
    }

    private static MockHttpServletRequestBuilder fromAddress(String address) {
        return post("/test/echo").with(request -> {
            request.setRemoteAddr(address);
            return request;
        });
    }
}
