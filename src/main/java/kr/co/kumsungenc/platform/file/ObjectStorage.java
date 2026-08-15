package kr.co.kumsungenc.platform.file;

import org.springframework.core.io.Resource;

import java.io.IOException;
import java.nio.file.Path;

public interface ObjectStorage {
    void put(String key, Path source, String contentType) throws IOException;
    StoredObject get(String key) throws IOException;
    void delete(String key) throws IOException;

    record StoredObject(Resource resource, long contentLength) {}
}
