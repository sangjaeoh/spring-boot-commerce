package com.commerce.member.entity;

import com.commerce.member.exception.InvalidEmailException;
import com.commerce.member.exception.MemberErrorCode;
import java.util.regex.Pattern;

/** 회원 이메일 값 객체다. 생성 시 형식을 검증한다. */
public record Email(String value) {

    private static final Pattern FORMAT = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public Email {
        if (!FORMAT.matcher(value).matches()) {
            throw new InvalidEmailException(MemberErrorCode.INVALID_EMAIL_FORMAT);
        }
    }

    public static Email of(String value) {
        return new Email(value);
    }
}
