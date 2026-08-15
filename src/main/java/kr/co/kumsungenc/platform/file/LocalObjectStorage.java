package kr.co.kumsungenc.platform.file;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.util.NoSuchElementException;

@Component
@ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalObjectStorage implements ObjectStorage {
    private final Path root;

    public LocalObjectStorage(@Value("${app.storage.local.root:${app.upload-dir:./uploads}}") String root) {
        this.root = Paths.get(root).toAbsolutePath().normalize();
    }

    @Override
    public void put(String key, Path source, String contentType) throws IOException {
        Path target = resolve(key);
        Files.createDirectories(target.getParent());
        Path temp = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
        try {
            Files.copy(source, temp, StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    @Override
    public StoredObject get(String key) throws IOException {
        Path target = resolve(key);
        if (!Files.isRegularFile(target)) throw new NoSuchElementException("파일을 찾을 수 없습니다.");
        return new StoredObject(new FileSystemResource(target), Files.size(target));
    }

    @Override
    public void delete(String key) throws IOException {
        Files.deleteIfExists(resolve(key));
    }

    private Path resolve(String key) {
        Path target = root.resolve(StorageKeys.normalize(key)).normalize();
        if (!target.startsWith(root)) throw new IllegalArgumentException("올바르지 않은 파일 저장 경로입니다.");
        return target;
    }
}
