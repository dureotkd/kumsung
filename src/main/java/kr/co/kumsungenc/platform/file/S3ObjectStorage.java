package kr.co.kumsungenc.platform.file;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.NoSuchElementException;

@Component
@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
public class S3ObjectStorage implements ObjectStorage {
    private final S3Client s3;
    private final String bucket;
    private final String prefix;

    public S3ObjectStorage(S3Client s3,
        @Value("${app.storage.s3.bucket}") String bucket,
        @Value("${app.storage.s3.prefix:private}") String prefix) {
        this.s3 = s3;
        this.bucket = required(bucket, "S3 버킷");
        this.prefix = trimSlashes(prefix);
    }

    @Override
    public void put(String key, Path source, String contentType) throws IOException {
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket).key(objectKey(key))
                .contentType(contentType)
                .serverSideEncryption(ServerSideEncryption.AES256)
                .build();
            s3.putObject(request, RequestBody.fromFile(source));
        } catch (S3Exception e) {
            throw new IOException("S3 파일 저장에 실패했습니다.", e);
        }
    }

    @Override
    public StoredObject get(String key) throws IOException {
        try {
            ResponseInputStream<GetObjectResponse> response = s3.getObject(GetObjectRequest.builder()
                .bucket(bucket).key(objectKey(key)).build());
            return new StoredObject(new InputStreamResource(response), response.response().contentLength());
        } catch (NoSuchKeyException e) {
            throw new NoSuchElementException("파일을 찾을 수 없습니다.");
        } catch (S3Exception e) {
            if (e.statusCode() == 404) throw new NoSuchElementException("파일을 찾을 수 없습니다.");
            throw new IOException("S3 파일 조회에 실패했습니다.", e);
        }
    }

    @Override
    public void delete(String key) throws IOException {
        try {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(objectKey(key)).build());
        } catch (S3Exception e) {
            throw new IOException("S3 파일 삭제에 실패했습니다.", e);
        }
    }

    private String objectKey(String key) {
        String normalized = StorageKeys.normalize(key);
        return prefix.isBlank() ? normalized : prefix + "/" + normalized;
    }

    private String trimSlashes(String value) {
        if (value == null) return "";
        return value.replaceAll("^/+|/+$", "");
    }

    private String required(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " 설정이 필요합니다.");
        return value.trim();
    }
}
