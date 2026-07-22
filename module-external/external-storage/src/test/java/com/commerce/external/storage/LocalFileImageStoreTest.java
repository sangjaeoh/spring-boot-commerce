package com.commerce.external.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFileImageStoreTest {

    @TempDir
    Path baseDir;

    @Test
    @DisplayName("store는 키 경로에 파일을 쓰고 URL을 반환한다")
    void storeWritesFileAndReturnsUrl() throws Exception {
        LocalFileImageStore store = new LocalFileImageStore(baseDir.toString());
        byte[] content = {1, 2, 3};

        String url = store.store("p1/image-1.png", content, "image/png");

        assertThat(url).isEqualTo("/files/p1/image-1.png");
        assertThat(Files.readAllBytes(baseDir.resolve("p1/image-1.png"))).isEqualTo(content);
    }

    @Test
    @DisplayName("remove는 보관된 파일을 지우고 없는 키는 아무 일도 하지 않는다")
    void removeDeletesFileAndIsIdempotent() {
        LocalFileImageStore store = new LocalFileImageStore(baseDir.toString());
        store.store("p1/image-1.png", new byte[] {1}, "image/png");

        store.remove("p1/image-1.png");

        assertThat(Files.exists(baseDir.resolve("p1/image-1.png"))).isFalse();
        assertThatCode(() -> store.remove("p1/image-1.png")).doesNotThrowAnyException();
    }
}
