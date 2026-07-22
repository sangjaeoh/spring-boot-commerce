package com.commerce.inquiry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.commerce.inquiry.exception.InquiryNotFoundException;
import com.commerce.inquiry.info.InquiryInfo;
import com.commerce.inquiry.service.InquiryAppender;
import com.commerce.inquiry.service.InquiryModifier;
import com.commerce.inquiry.service.InquiryReader;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestConstructor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * inquiry 도메인의 영속 이음새를 실 PostgreSQL로 검증하는 테스트다.
 *
 * <p>{@code ddl-auto=validate} 정합, 작성·답변, 최신 문의 우선 페이지를 확인한다.
 */
@DataJpaTest(
        properties = {
            "spring.jpa.hibernate.ddl-auto=validate",
            "spring.flyway.enabled=true",
            "spring.flyway.locations=classpath:db/migration/inquiry",
            "spring.flyway.schemas=inquiry",
            "spring.flyway.default-schema=inquiry"
        })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({InquiryAppender.class, InquiryModifier.class, InquiryReader.class})
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class InquiryPersistenceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

    private final InquiryAppender inquiryAppender;
    private final InquiryModifier inquiryModifier;
    private final InquiryReader inquiryReader;

    InquiryPersistenceTest(
            InquiryAppender inquiryAppender, InquiryModifier inquiryModifier, InquiryReader inquiryReader) {
        this.inquiryAppender = inquiryAppender;
        this.inquiryModifier = inquiryModifier;
        this.inquiryReader = inquiryReader;
    }

    @Test
    @DisplayName("작성한 문의가 상품별 페이지에 본문·비밀글 여부·미답변 상태로 보인다")
    void writeShowsInquiryInProductPage() {
        UUID memberId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        UUID inquiryId = inquiryAppender.write(memberId, productId, "배송은 얼마나 걸리나요?", true);

        assertThat(inquiryReader
                        .getProductPage(productId, PageRequest.of(0, 10))
                        .getContent())
                .singleElement()
                .satisfies(info -> {
                    assertThat(info.id()).isEqualTo(inquiryId);
                    assertThat(info.memberId()).isEqualTo(memberId);
                    assertThat(info.content()).isEqualTo("배송은 얼마나 걸리나요?");
                    assertThat(info.secret()).isTrue();
                    assertThat(info.answer()).isNull();
                    assertThat(info.writtenAt()).isNotNull();
                });
    }

    @Test
    @DisplayName("답변이 페이지에 반영되고, 없는 문의 답변은 부재로 거부된다")
    void answerAppliesToExistingInquiryOnly() {
        UUID productId = UUID.randomUUID();
        UUID inquiryId = inquiryAppender.write(UUID.randomUUID(), productId, "재입고 예정이 있나요?", false);

        inquiryModifier.answer(inquiryId, "다음 주 재입고 예정입니다.");
        assertThat(inquiryReader
                        .getProductPage(productId, PageRequest.of(0, 10))
                        .getContent())
                .singleElement()
                .satisfies(info -> assertThat(info.answer()).isEqualTo("다음 주 재입고 예정입니다."));

        assertThatThrownBy(() -> inquiryModifier.answer(UUID.randomUUID(), "답변"))
                .isInstanceOf(InquiryNotFoundException.class);
    }

    @Test
    @DisplayName("상품별 페이지는 최신 문의 우선으로 정렬된다")
    void productPageOrdersLatestFirst() {
        UUID productId = UUID.randomUUID();
        UUID firstInquiryId = inquiryAppender.write(UUID.randomUUID(), productId, "먼저 쓴 문의", false);
        UUID secondInquiryId = inquiryAppender.write(UUID.randomUUID(), productId, "나중에 쓴 문의", false);

        assertThat(inquiryReader
                        .getProductPage(productId, PageRequest.of(0, 10))
                        .getContent())
                .extracting(InquiryInfo::id)
                .containsExactly(secondInquiryId, firstInquiryId);
    }
}
