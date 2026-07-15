package com.commerce.api.presentation.v1.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * 쿠폰 발급 요청이다.
 *
 * <p>인증이 범위 밖이라 발급 대상 회원을 요청이 싣는다.
 */
public record CouponIssuanceRequest(@NotNull UUID memberId) {}
