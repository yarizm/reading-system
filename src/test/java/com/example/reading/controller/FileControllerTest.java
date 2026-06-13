package com.example.reading.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.reading.entity.SysBook;
import com.example.reading.entity.SysChapter;
import com.example.reading.mapper.SysChapterMapper;
import com.example.reading.service.AuthContextService;
import com.example.reading.service.ISysBookService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileControllerTest {

    @TempDir
    Path tempDir;

    @Mock
    private ISysBookService sysBookService;

    @Mock
    private SysChapterMapper chapterMapper;

    @Mock
    private AuthContextService authContextService;

    @Mock
    private HttpServletRequest request;

    private FileController buildController() {
        FileController controller = new FileController(sysBookService, chapterMapper, authContextService);
        ReflectionTestUtils.setField(controller, "uploadPath", tempDir.toString());
        return controller;
    }

    @Test
    void txtFileIsAllowedWhenAnyAssociatedBookIsViewable() throws Exception {
        Files.writeString(tempDir.resolve("shared.txt"), "content");

        SysBook publicBook = new SysBook();
        publicBook.setId(1L);
        publicBook.setStatus(2);

        SysBook privateBook = new SysBook();
        privateBook.setId(2L);
        privateBook.setStatus(0);

        when(sysBookService.list(any(QueryWrapper.class))).thenReturn(List.of(publicBook, privateBook));
        when(authContextService.isPublicBook(publicBook)).thenReturn(true);

        ResponseEntity<Resource> response = buildController().getFile("shared.txt", null, request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void publicImageIsAlwaysAccessibleWithoutAuth() throws Exception {
        Files.writeString(tempDir.resolve("cover.jpg"), "img");

        ResponseEntity<Resource> response = buildController().getFile("cover.jpg", null, request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void publicAudioIsAlwaysAccessibleWithoutAuth() throws Exception {
        Files.write(tempDir.resolve("audio.mp3"), new byte[]{1, 2, 3});

        ResponseEntity<Resource> response = buildController().getFile("audio.mp3", null, request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void pathTraversalFilenameReturns404() throws Exception {
        // 无需创建真实文件：路径穿越在 normalize + startsWith 校验阶段即被拦截
        ResponseEntity<Resource> response = buildController().getFile("../escape.txt", null, request);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void txtWithoutAssociatedBookReturns403() throws Exception {
        Files.writeString(tempDir.resolve("orphan.txt"), "content");

        when(sysBookService.list(any(QueryWrapper.class))).thenReturn(List.of());

        ResponseEntity<Resource> response = buildController().getFile("orphan.txt", null, request);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void privateTxtReturns403WithoutAccess() throws Exception {
        Files.writeString(tempDir.resolve("private.txt"), "content");

        SysBook privateBook = new SysBook();
        privateBook.setId(2L);
        privateBook.setStatus(0);

        when(sysBookService.list(any(QueryWrapper.class))).thenReturn(List.of(privateBook));
        when(authContextService.isPublicBook(privateBook)).thenReturn(false);
        when(authContextService.currentUserId(request)).thenReturn(null);
        when(authContextService.canViewBook(privateBook, (Long) null)).thenReturn(false);

        ResponseEntity<Resource> response = buildController().getFile("private.txt", null, request);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void privateTxtAllowedWithValidToken() throws Exception {
        Files.writeString(tempDir.resolve("private.txt"), "content");

        SysBook privateBook = new SysBook();
        privateBook.setId(2L);
        privateBook.setStatus(0);
        privateBook.setUploaderId(5L);

        when(sysBookService.list(any(QueryWrapper.class))).thenReturn(List.of(privateBook));
        when(authContextService.isPublicBook(privateBook)).thenReturn(false);
        when(authContextService.currentUserId(request)).thenReturn(null);
        when(authContextService.currentUserId("valid-token")).thenReturn(5L);
        when(authContextService.canViewBook(privateBook, 5L)).thenReturn(true);

        ResponseEntity<Resource> response = buildController().getFile("private.txt", "valid-token", request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void chapterTtsAllowedForPublicBook() throws Exception {
        Files.writeString(tempDir.resolve("chapter_tts_book_10_intro.mp3"), "audio");

        SysChapter chapter = new SysChapter();
        chapter.setBookId(7L);

        SysBook publicBook = new SysBook();
        publicBook.setId(7L);
        publicBook.setStatus(2);

        when(chapterMapper.selectById(10L)).thenReturn(chapter);
        when(sysBookService.getById(7L)).thenReturn(publicBook);
        when(authContextService.isPublicBook(publicBook)).thenReturn(true);

        ResponseEntity<Resource> response = buildController().getFile("chapter_tts_book_10_intro.mp3", null, request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void chapterTtsForbiddenForPrivateBookWithoutAccess() throws Exception {
        Files.writeString(tempDir.resolve("chapter_tts_book_10_intro.mp3"), "audio");

        SysChapter chapter = new SysChapter();
        chapter.setBookId(7L);

        SysBook privateBook = new SysBook();
        privateBook.setId(7L);
        privateBook.setStatus(0);

        when(chapterMapper.selectById(10L)).thenReturn(chapter);
        when(sysBookService.getById(7L)).thenReturn(privateBook);
        when(authContextService.isPublicBook(privateBook)).thenReturn(false);
        when(authContextService.currentUserId(request)).thenReturn(null);
        when(authContextService.canViewBook(privateBook, (Long) null)).thenReturn(false);

        ResponseEntity<Resource> response = buildController().getFile("chapter_tts_book_10_intro.mp3", null, request);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void chapterTtsWithOverflowingDigitsReturns403Not500() throws Exception {
        // 数字段超过 Long.MAX_VALUE，解析失败必须按无权限处理，而不是抛 500
        String fileName = "chapter_tts_book_99999999999999999999999999_overflow.mp3";
        Files.writeString(tempDir.resolve(fileName), "audio");

        ResponseEntity<Resource> response = buildController().getFile(fileName, null, request);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void chapterTtsWithNonMatchingNameReturns403() throws Exception {
        Files.writeString(tempDir.resolve("chapter_tts_malformed.mp3"), "audio");

        ResponseEntity<Resource> response = buildController().getFile("chapter_tts_malformed.mp3", null, request);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
    }
}
