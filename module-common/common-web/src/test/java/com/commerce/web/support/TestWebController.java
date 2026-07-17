package com.commerce.web.support;

import com.commerce.web.auth.AdminOnly;
import com.commerce.web.auth.AuthUser;
import com.commerce.web.paging.PaginationRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 핸들러·필터 검증용 테스트 엔드포인트. 각 경로가 특정 예외·경로를 재현한다. */
@RestController
public class TestWebController {

    @PostMapping("/test/base")
    public void base() {
        throw new TestBoundaryException();
    }

    @GetMapping("/test/optimistic")
    public void optimistic() {
        throw new ObjectOptimisticLockingFailureException(Object.class, "id-1");
    }

    @GetMapping("/test/iae")
    public void illegalArgument() {
        throw new IllegalArgumentException("호출자 보장 선행조건 위반");
    }

    @PostMapping("/test/validate")
    public void validate(@Valid @RequestBody TestRequest request) {}

    @GetMapping("/test/param")
    public void param(@RequestParam("page") @Min(0) int page) {}

    @GetMapping("/test/pagination")
    public ResponseEntity<String> pagination(@Valid PaginationRequest request) {
        return ResponseEntity.ok(request.zeroBasedPage() + ":" + request.size());
    }

    @PostMapping("/test/echo")
    public ResponseEntity<String> echo() {
        return ResponseEntity.ok("ok");
    }

    @GetMapping("/test/echo")
    public ResponseEntity<String> echoGet() {
        return ResponseEntity.ok("ok");
    }

    @GetMapping("/test/auth-user")
    public ResponseEntity<String> authUser(
            @RequestAttribute(name = AuthUser.ATTRIBUTE, required = false) @Nullable AuthUser authUser) {
        return ResponseEntity.ok(
                authUser == null ? "anonymous" : authUser.memberId().toString());
    }

    @GetMapping("/test/principal")
    public ResponseEntity<String> principal(AuthUser authUser) {
        return ResponseEntity.ok(authUser.memberId().toString());
    }

    @AdminOnly
    @GetMapping("/test/admin")
    public ResponseEntity<String> admin() {
        return ResponseEntity.ok("admin-ok");
    }
}
