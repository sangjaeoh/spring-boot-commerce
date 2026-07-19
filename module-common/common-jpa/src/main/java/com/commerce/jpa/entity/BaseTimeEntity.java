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

/** 생성·수정 시각을 JPA Auditing으로 채우는 엔티티 공통 상위 타입이다. */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseTimeEntity<ID extends Serializable> implements Persistable<ID> {

    /** 최초 저장 시각. 저장 전에는 없다. */
    @CreatedDate
    @Column(updatable = false)
    private Instant createdAt;

    /** 마지막 수정 시각. 최초 저장 시에도 채워져 생성 시각과 같다. */
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
