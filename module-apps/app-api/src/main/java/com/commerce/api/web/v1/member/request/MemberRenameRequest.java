package com.commerce.api.web.v1.member.request;

import jakarta.validation.constraints.NotBlank;

/** 회원 표시 이름 변경 요청이다. 이메일은 불변이라 받지 않는다. */
public record MemberRenameRequest(@NotBlank String name) {}
