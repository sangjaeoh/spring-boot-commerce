package com.commerce.web.support;

import com.commerce.core.exception.BaseException;

/** 경계에 도달하는 {@link BaseException} 검증용 테스트 예외. */
public class TestBoundaryException extends BaseException {

    public TestBoundaryException() {
        super(TestErrorCode.SAMPLE);
    }
}
