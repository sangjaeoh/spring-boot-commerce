package com.commerce.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import java.io.Serializable;
import java.time.Instant;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.domain.Persistable;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 생성·수정 시각을 JPA Auditing으로 채우는 엔티티 공통 상위 타입이다.
 *
 * <p>{@link Persistable}을 구현해 수동 UUIDv7 PK가 유발하는 merge penalty를 막는다 —
 * {@code createdAt == null}이면 신규로 판정해 {@code persist()}로 직행한다. 식별자 기반
 * 동등성이라 {@code orphanRemoval} Set·LAZY 프록시·detached 비교에서 안전하다.
 *
 * @param <ID> 식별자 타입
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseTimeEntity<ID extends Serializable> implements Persistable<ID> {

    @CreatedDate
    @Column(updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column
    private Instant updatedAt;

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean isNew() {
        return this.createdAt == null;
    }

    @Override
    public abstract ID getId();

    @Override
    public final boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BaseTimeEntity<?> that)) {
            return false;
        }
        return getId() != null && getId().equals(that.getId());
    }

    @Override
    public final int hashCode() {
        return getId() == null ? 0 : getId().hashCode();
    }
}
