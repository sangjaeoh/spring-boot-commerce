package com.commerce.inquiry.domain;

import com.commerce.core.id.UuidV7Generator;
import com.commerce.jpa.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 회원이 상품에 남긴 문의다. 비밀글이면 작성자·관리자만 내용을 열람한다. */
@Entity
@Table(schema = "inquiry", name = "inquiry")
public class Inquiry extends BaseTimeEntity<UUID> {

    private static final int MAX_CONTENT_LENGTH = 1000;

    /** 문의 식별자. 생성 시각 순서를 담은 UUIDv7. */
    @Id
    private UUID id;

    /** 작성한 회원 식별자. member 도메인 논리 참조. */
    @Column(name = "member_id")
    private UUID memberId;

    /** 문의 대상 상품 식별자. product 도메인 논리 참조. */
    @Column(name = "product_id")
    private UUID productId;

    /** 문의 본문(1~1000자). */
    @Column
    private String content;

    /** 비밀글 여부. 비밀글 내용은 작성자·관리자만 열람한다. */
    @Column
    private boolean secret;

    /** 관리자 답변 본문(1~1000자). 미답변이면 없다. */
    @Column
    private @Nullable String answer;

    protected Inquiry() {}

    private Inquiry(UUID id, UUID memberId, UUID productId, String content, boolean secret) {
        this.id = id;
        this.memberId = memberId;
        this.productId = productId;
        this.content = content;
        this.secret = secret;
    }

    /**
     * 문의를 생성한다.
     *
     * @throws InvalidInquiryException 본문이 허용 범위를 벗어나면
     */
    public static Inquiry create(UUID memberId, UUID productId, String content, boolean secret) {
        validateContent(content);
        return new Inquiry(UuidV7Generator.generate(), memberId, productId, content, secret);
    }

    /**
     * 답변을 단다. 이미 답변이 있으면 덮어쓴다.
     *
     * @throws InvalidInquiryException 답변 본문이 허용 범위를 벗어나면
     */
    public void answer(String content) {
        validateContent(content);
        this.answer = content;
    }

    /** 본문 허용 범위를 검증한다. */
    private static void validateContent(String content) {
        if (content.isBlank() || content.length() > MAX_CONTENT_LENGTH) {
            throw new InvalidInquiryException(InquiryErrorCode.INVALID_CONTENT);
        }
    }

    @Override
    public UUID getId() {
        return this.id;
    }

    public UUID getMemberId() {
        return memberId;
    }

    public UUID getProductId() {
        return productId;
    }

    public String getContent() {
        return content;
    }

    public boolean isSecret() {
        return secret;
    }

    public @Nullable String getAnswer() {
        return answer;
    }
}
