package com.commerce.external.storage;

import com.commerce.product.port.ImageStore;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 로컬 파일시스템에 보관하는 개발용 {@link ImageStore} 구현이다. */
@Component
final class LocalFileImageStore implements ImageStore {

    private static final String URL_PREFIX = "/files/";

    private final Path baseDir;

    LocalFileImageStore(@Value("${storage.local.base-dir:${java.io.tmpdir}/commerce-product-images}") String baseDir) {
        this.baseDir = Path.of(baseDir);
    }

    @Override
    public String store(String key, byte[] content, String contentType) {
        Path target = baseDir.resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return URL_PREFIX + key;
    }

    @Override
    public void remove(String key) {
        try {
            Files.deleteIfExists(baseDir.resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
