package com.commerce.member.domain;

import com.commerce.core.id.UuidV7Generator;
import com.commerce.jpa.entity.BaseTimeEntity;
import com.commerce.member.domain.converter.EmailConverter;
import com.commerce.member.domain.exception.MemberErrorCode;
import com.commerce.member.domain.exception.MemberStatusException;
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

/** 회원 애그리거트 루트다. 식별(이메일·표시 이름)과 자격증명(패스워드 해시)·역할을 소유한다. */
@Entity
@Table(schema = "member", name = "member")
public class Member extends BaseTimeEntity<UUID> {

    /** 회원 식별자. 생성 시각 순서를 담은 UUIDv7. */
    @Id
    private UUID id;

    /** 회원 이메일. 활성 회원 사이에서 유니크한 식별 키. 생성 후 바뀌지 않는다. */
    @Convert(converter = EmailConverter.class)
    @Column(name = "email")
    private Email email;

    /** 회원 표시 이름. */
    @Column(name = "name")
    private String name;

    /** 패스워드의 bcrypt 해시(60자). */
    @Column(name = "password_hash")
    private String passwordHash;

    /** 회원 역할. 가입은 항상 구매자이고 관리자는 기동 시딩으로만 부여된다. 생성 후 바뀌지 않는다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    private MemberRole role;

    /** 회원 계정 상태. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private MemberStatus status;

    /** 정지 사유. 정지 상태일 때만 있다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "suspension_reason")
    @Nullable
    private SuspensionReason suspensionReason;

    /** 이메일 소유 인증 시각. 미인증이면 없다. */
    @Column(name = "email_verified_at")
    @Nullable
    private Instant emailVerifiedAt;

    /** 탈퇴(논리삭제) 시각. 탈퇴 여부는 {@code status}가 아니라 이 값의 존재로 나타낸다. */
    @Column(name = "deleted_at")
    @Nullable
    private Instant deletedAt;

    /** 탈퇴 사유. 탈퇴한 회원에만 있다. */
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

    /**
     * 회원을 정지하고 사유를 기록한다.
     *
     * @throws MemberStatusException 활성 상태가 아니면
     */
    public void suspend(SuspensionReason reason) {
        if (status != MemberStatus.ACTIVE) {
            throw new MemberStatusException(MemberErrorCode.INVALID_STATUS_TRANSITION);
        }
        this.status = MemberStatus.SUSPENDED;
        this.suspensionReason = reason;
    }

    /**
     * 정지를 해제하고 사유를 지운다.
     *
     * @throws MemberStatusException 정지 상태가 아니면
     */
    public void reinstate() {
        if (status != MemberStatus.SUSPENDED) {
            throw new MemberStatusException(MemberErrorCode.INVALID_STATUS_TRANSITION);
        }
        this.status = MemberStatus.ACTIVE;
        this.suspensionReason = null;
    }

    /** 표시 이름을 바꾼다. */
    public void rename(String newName) {
        this.name = newName;
    }

    /** 패스워드 해시를 새 값으로 교체한다. 자격증명은 이미 해시된 값을 받는다(평문 비보관). */
    public void replacePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
    }

    /** 이메일 소유 인증을 기록한다. */
    public void verifyEmail(Instant now) {
        this.emailVerifiedAt = now;
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

    public @Nullable Instant getEmailVerifiedAt() {
        return emailVerifiedAt;
    }

    public @Nullable Instant getDeletedAt() {
        return deletedAt;
    }

    public @Nullable WithdrawalReason getWithdrawalReason() {
        return withdrawalReason;
    }
}
