package kr.co.kumsungenc.platform.file;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import java.util.Collections;
import static org.mockito.Mockito.*;

import static org.junit.jupiter.api.Assertions.*;

class FileValidationServiceTest {
    private final FileValidationService service=new FileValidationService();

    @Test
    void acceptsPdfWithValidSignature() throws Exception {
        var file=new MockMultipartFile("files","drawing.pdf","application/pdf",
            "%PDF-1.4\n%%EOF".getBytes());
        assertEquals("pdf",service.validateQuoteFile(file));
    }

    @Test
    void rejectsFileWhoseContentDoesNotMatchExtension() {
        var file=new MockMultipartFile("files","drawing.pdf","application/pdf",
            "not a pdf".getBytes());
        var error=assertThrows(IllegalArgumentException.class,()->service.validateQuoteFile(file));
        assertTrue(error.getMessage().contains("일치하지 않습니다"));
    }

    @Test
    void acceptsAnyQuoteFileExtension() throws Exception {
        var file=new MockMultipartFile("files","assembly.step","application/octet-stream",
            "ISO-10303-21;".getBytes());
        assertEquals("step",service.validateQuoteFile(file));
    }

    @Test
    void acceptsQuoteFileWithoutExtension() throws Exception {
        var file=new MockMultipartFile("files","README","application/octet-stream",
            "project notes".getBytes());
        assertEquals("",service.validateQuoteFile(file));
    }

    @Test
    void rejectsPathTraversalName() {
        var file=new MockMultipartFile("files","../drawing.pdf","application/pdf",
            "%PDF-1.4".getBytes());
        assertThrows(IllegalArgumentException.class,()->service.validateQuoteFile(file));
    }

    @Test
    void rejectsMoreThanTwentyFilesInOneRequest() {
        var file=new MockMultipartFile("files","drawing.pdf","application/pdf","%PDF-1.4".getBytes());
        assertThrows(IllegalArgumentException.class,()->service.validateBatch(Collections.nCopies(21,file)));
    }

    @Test
    void rejectsAggregateSizeOverFourGigabytes() {
        MultipartFile file=mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(4L*1024*1024*1024+1);
        assertThrows(IllegalArgumentException.class,()->service.validateBatch(java.util.List.of(file)));
    }

    @Test
    void rejectsQuoteFileOverTwoGigabytes() {
        MultipartFile file=mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("oversized.bin");
        when(file.getSize()).thenReturn(2L*1024*1024*1024+1);
        assertThrows(IllegalArgumentException.class,()->service.validateQuoteFile(file));
    }
}
