package com.commerce.member.domain;

import com.commerce.core.id.UuidV7Generator;
import com.commerce.jpa.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 회원 배송지 애그리거트 루트다. */
@Entity
@Table(schema = "member", name = "member_address")
public class MemberAddress extends BaseTimeEntity<UUID> {

    /** 배송지 식별자. 생성 시각 순서를 담은 UUIDv7. */
    @Id
    private UUID id;

    /** 배송지를 소유한 회원 식별자. member 애그리거트 논리 참조. */
    @Column(name = "member_id")
    private UUID memberId;

    /** 수령인 이름. */
    @Column(name = "recipient_name")
    private String recipientName;

    /** 우편번호. */
    @Column(name = "zip_code")
    private String zipCode;

    /** 도로명 주소. */
    @Column(name = "road_address")
    private String roadAddress;

    /** 상세 주소. 없을 수 있다. */
    @Column(name = "detail_address")
    @Nullable
    private String detailAddress;

    /** 수령인 연락처. */
    @Column(name = "phone")
    private String phone;

    /** 기본 배송지 여부. 회원당 최대 하나다. */
    @Column(name = "is_default")
    private boolean defaultAddress;

    protected MemberAddress() {}

    private MemberAddress(
            UUID id,
            UUID memberId,
            String recipientName,
            String zipCode,
            String roadAddress,
            @Nullable String detailAddress,
            String phone,
            boolean defaultAddress) {
        this.id = id;
        this.memberId = memberId;
        this.recipientName = recipientName;
        this.zipCode = zipCode;
        this.roadAddress = roadAddress;
        this.detailAddress = detailAddress;
        this.phone = phone;
        this.defaultAddress = defaultAddress;
    }

    /** 회원 배송지를 생성한다. 기본 여부는 호출자가 결정한다(첫 배송지 자동 기본 등). */
    public static MemberAddress create(
            UUID memberId,
            String recipientName,
            String zipCode,
            String roadAddress,
            @Nullable String detailAddress,
            String phone,
            boolean defaultAddress) {
        return new MemberAddress(
                UuidV7Generator.generate(),
                memberId,
                recipientName,
                zipCode,
                roadAddress,
                detailAddress,
                phone,
                defaultAddress);
    }

    /** 배송지 정보를 수정한다. 기본 여부는 바꾸지 않는다. */
    public void revise(
            String recipientName, String zipCode, String roadAddress, @Nullable String detailAddress, String phone) {
        this.recipientName = recipientName;
        this.zipCode = zipCode;
        this.roadAddress = roadAddress;
        this.detailAddress = detailAddress;
        this.phone = phone;
    }

    /** 기본 배송지로 지정한다. 회원당 단일성은 호출자가 보장한다. */
    public void markDefault() {
        this.defaultAddress = true;
    }

    @Override
    public UUID getId() {
        return id;
    }

    public UUID getMemberId() {
        return memberId;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public String getZipCode() {
        return zipCode;
    }

    public String getRoadAddress() {
        return roadAddress;
    }

    public @Nullable String getDetailAddress() {
        return detailAddress;
    }

    public String getPhone() {
        return phone;
    }

    public boolean isDefaultAddress() {
        return defaultAddress;
    }
}
