package kr.co.kumsungenc.platform.file;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.nio.file.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class S3ObjectStorageTest {
    @TempDir Path temp;

    @Test
    void uploadsEncryptedPrivateObjectAndDeletesTheSameKey() throws Exception {
        S3Client client=mock(S3Client.class);
        when(client.putObject(any(PutObjectRequest.class),any(RequestBody.class)))
            .thenReturn(PutObjectResponse.builder().build());
        when(client.deleteObject(any(DeleteObjectRequest.class)))
            .thenReturn(DeleteObjectResponse.builder().build());
        Path source=temp.resolve("drawing.pdf");Files.writeString(source,"test");
        S3ObjectStorage storage=new S3ObjectStorage(client,"kumsung-files","private");

        storage.put("KS-1/drawing.pdf",source,"application/pdf");
        storage.delete("KS-1/drawing.pdf");

        ArgumentCaptor<PutObjectRequest> put=ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(client).putObject(put.capture(),any(RequestBody.class));
        assertThat(put.getValue().bucket()).isEqualTo("kumsung-files");
        assertThat(put.getValue().key()).isEqualTo("private/KS-1/drawing.pdf");
        assertThat(put.getValue().serverSideEncryption()).isEqualTo(ServerSideEncryption.AES256);
        ArgumentCaptor<DeleteObjectRequest> delete=ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(client).deleteObject(delete.capture());
        assertThat(delete.getValue().key()).isEqualTo("private/KS-1/drawing.pdf");
    }
}
