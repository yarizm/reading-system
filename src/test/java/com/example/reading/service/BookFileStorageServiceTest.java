package com.example.reading.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookFileStorageServiceTest {

    private final BookFileStorageService service = new BookFileStorageService();

    @TempDir
    private Path tempDir;

    @Test
    void storeUploadPersistsFileAndReturnsFilesUrl() throws Exception {
        ReflectionTestUtils.setField(service, "uploadPath", tempDir.toString());
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "book.txt",
                "text/plain",
                "hello".getBytes()
        );

        String url = service.storeUpload(file);

        assertThat(url).startsWith("/files/").endsWith(".txt");
        String storedName = url.substring("/files/".length());
        assertThat(tempDir.resolve(storedName)).hasContent("hello");
    }

    @Test
    void storeUploadRejectsUnsupportedFileType() {
        ReflectionTestUtils.setField(service, "uploadPath", tempDir.toString());
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "payload.exe",
                "application/octet-stream",
                new byte[]{1}
        );

        assertThatThrownBy(() -> service.storeUpload(file))
                .isInstanceOf(BookFileStorageService.UnsupportedFileTypeException.class);
    }

    @Test
    void resolveStoredFileUsesConfiguredUploadRoot() {
        ReflectionTestUtils.setField(service, "uploadPath", tempDir.toString());

        File file = service.resolveStoredFile("/files/book.txt");

        assertThat(file.getPath()).isEqualTo(tempDir + File.separator + "book.txt");
    }

    @Test
    void resolveStoredFileTreatsBackslashTraversalAsAStoredFileNameOnly() {
        ReflectionTestUtils.setField(service, "uploadPath", tempDir.toString());

        File file = service.resolveStoredFile("/files/..\\secret.txt");

        assertThat(file.toPath()).isEqualTo(tempDir.resolve("secret.txt").toAbsolutePath().normalize());
    }

    @Test
    void resolveStoredFileRejectsBlankPath() {
        ReflectionTestUtils.setField(service, "uploadPath", tempDir.toString());

        assertThatThrownBy(() -> service.resolveStoredFile(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
