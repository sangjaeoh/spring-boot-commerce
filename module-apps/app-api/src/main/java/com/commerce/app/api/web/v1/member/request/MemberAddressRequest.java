package com.commerce.app.api.web.v1.member.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import org.jspecify.annotations.Nullable;

/** 회원 배송지 등록·수정 요청이다. 기본 여부는 기본 지정 액션이 따로 다룬다. */
@Schema(description = "회원 배송지 등록·수정 요청")
public record MemberAddressRequest(
        @Schema(description = "수령인 이름") @NotBlank String recipientName,
        @Schema(description = "우편번호") @NotBlank String zipCode,
        @Schema(description = "도로명 주소") @NotBlank String roadAddress,

        @Schema(description = "상세 주소. 선택 항목", nullable = true) @Nullable
        String detailAddress,

        @Schema(description = "수령인 연락처") @NotBlank String phone) {}
