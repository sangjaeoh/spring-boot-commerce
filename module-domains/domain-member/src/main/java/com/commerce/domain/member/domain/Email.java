package com.commerce.domain.member.domain;

import com.commerce.domain.member.domain.exception.InvalidEmailException;
import com.commerce.domain.member.domain.exception.MemberErrorCode;
import java.util.regex.Pattern;

/** 회원 이메일 값 객체다. */
public record Email(String value) {

    private static final Pattern FORMAT = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public Email {
        if (!FORMAT.matcher(value).matches()) {
            throw new InvalidEmailException(MemberErrorCode.INVALID_EMAIL_FORMAT);
        }
    }

    /**
     * 이메일을 만든다.
     *
     * @throws InvalidEmailException 이메일 형식이 올바르지 않으면
     */
    public static Email of(String value) {
        return new Email(value);
    }
}
