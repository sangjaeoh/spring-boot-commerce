package com.commerce.common.cache;

import java.time.Duration;
import tools.jackson.databind.JavaType;

/** 캐시 이름 하나의 TTL·값 타입 등록 정보다. 이름별 TTL·타입 고정 직렬화기 조립의 입력이다. */
public record CacheRegistration(String name, Duration ttl, JavaType valueType) {}
