package com.commerce.web.paging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class PaginationResponseTest {

    @Test
    @DisplayName("0-based Page가 1-based 페이지 메타로 보정된다")
    void convertsZeroBasedPageToOneBasedMeta() {
        PageImpl<String> page = new PageImpl<>(List.of("a", "b"), PageRequest.of(1, 2), 5);

        PaginationResponse response = PaginationResponse.from(page);

        assertThat(response.number()).isEqualTo(2);
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.totalElements()).isEqualTo(5);
        assertThat(response.totalPages()).isEqualTo(3);
    }
}
