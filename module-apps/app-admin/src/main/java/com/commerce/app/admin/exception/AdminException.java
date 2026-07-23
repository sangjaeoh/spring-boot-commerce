package com.commerce.app.admin.exception;

import com.commerce.common.core.exception.BaseException;

/** 어드민 파사드가 크로스 도메인 정책을 거부할 때 던지는 예외다. */
public class AdminException extends BaseException {

    public AdminException(AdminErrorCode errorCode) {
        super(errorCode);
    }
}
