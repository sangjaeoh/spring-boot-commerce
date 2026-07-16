package com.commerce.member.entity;

import com.commerce.core.id.UuidV7Generator;
import com.commerce.jpa.entity.BaseTimeEntity;
import com.commerce.member.exception.MemberErrorCode;
import com.commerce.member.exception.MemberStatusException;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 회원 애그리거트 루트다. 식별(이메일·표시 이름)과 자격증명(패스워드 해시)·역할을 소유한다.
 *
 * <p>정지(status)와 탈퇴(deletedAt)는 독립 축이라 겹치지 않는다. 최초 상태는 {@code ACTIVE}다.
 */
@Entity
@Table(schema = "member", name = "member")
public class Member extends BaseTimeEntity<UUID> {

    @Id
    private UUID id;

    @Convert(converter = EmailConverter.class)
    @Column(name = "email")
    private Email email;

    @Column(name = "name")
    private String name;

    @Column(name = "password_hash")
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    private MemberRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private MemberStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "suspension_reason")
    @Nullable
    private SuspensionReason suspensionReason;

    @Column(name = "deleted_at")
    @Nullable
    private Instant deletedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "withdrawal_reason")
    @Nullable
    private WithdrawalReason withdrawalReason;

    protected Member() {}

    private Member(UUID id, Email email, String name, String passwordHash, MemberRole role) {
        this.id = id;
        this.email = email;
        this.name = name;
        this.passwordHash = passwordHash;
        this.role = role;
        this.status = MemberStatus.ACTIVE;
    }

    /** 활성({@code ACTIVE}) 회원을 역할과 함께 생성한다. 자격증명은 이미 해시된 값을 받는다(평문 비보관). */
    public static Member create(Email email, String name, String passwordHash, MemberRole role) {
        return new Member(UuidV7Generator.generate(), email, name, passwordHash, role);
    }

    /** 회원을 정지하고 사유를 기록한다. */
    public void suspend(SuspensionReason reason) {
        if (status != MemberStatus.ACTIVE) {
            throw new MemberStatusException(MemberErrorCode.INVALID_STATUS_TRANSITION);
        }
        this.status = MemberStatus.SUSPENDED;
        this.suspensionReason = reason;
    }

    /** 정지를 해제하고 사유를 지운다. */
    public void reinstate() {
        if (status != MemberStatus.SUSPENDED) {
            throw new MemberStatusException(MemberErrorCode.INVALID_STATUS_TRANSITION);
        }
        this.status = MemberStatus.ACTIVE;
        this.suspensionReason = null;
    }

    /** 표시 이름을 바꾼다. 이메일은 불변이다. */
    public void rename(String newName) {
        this.name = newName;
    }

    /** 탈퇴 사유와 함께 논리삭제한다. */
    public void delete(WithdrawalReason reason, Instant now) {
        this.deletedAt = now;
        this.withdrawalReason = reason;
    }

    @Override
    public UUID getId() {
        return this.id;
    }

    public Email getEmail() {
        return email;
    }

    public String getName() {
        return name;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public MemberRole getRole() {
        return role;
    }

    public MemberStatus getStatus() {
        return status;
    }

    public @Nullable SuspensionReason getSuspensionReason() {
        return suspensionReason;
    }

    public @Nullable Instant getDeletedAt() {
        return deletedAt;
    }

    public @Nullable WithdrawalReason getWithdrawalReason() {
        return withdrawalReason;
    }
}
