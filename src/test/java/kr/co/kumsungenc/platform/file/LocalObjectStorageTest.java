package kr.co.kumsungenc.platform.file;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.*;

class LocalObjectStorageTest {
    @TempDir Path temp;

    @Test
    void storesLoadsAndDeletesInsideConfiguredRoot() throws Exception {
        Path root=temp.resolve("objects");
        Path source=temp.resolve("source.txt");
        Files.writeString(source,"storage-test",StandardCharsets.UTF_8);
        LocalObjectStorage storage=new LocalObjectStorage(root.toString());

        storage.put("quotes/receipt/file.txt",source,"text/plain");
        ObjectStorage.StoredObject found=storage.get("quotes/receipt/file.txt");

        assertThat(found.contentLength()).isEqualTo(12);
        assertThat(found.resource().getContentAsString(StandardCharsets.UTF_8)).isEqualTo("storage-test");
        storage.delete("quotes/receipt/file.txt");
        assertThatThrownBy(()->storage.get("quotes/receipt/file.txt"))
            .isInstanceOf(NoSuchElementException.class);
    }
}
