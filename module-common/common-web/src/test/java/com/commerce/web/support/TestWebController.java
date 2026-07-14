package com.commerce.web.support;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    @PostMapping("/test/echo")
    public ResponseEntity<String> echo() {
        return ResponseEntity.ok("ok");
    }

    @GetMapping("/test/echo")
    public ResponseEntity<String> echoGet() {
        return ResponseEntity.ok("ok");
    }
}
