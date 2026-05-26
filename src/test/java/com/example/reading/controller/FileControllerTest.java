package com.example.reading.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.reading.entity.SysBook;
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


        FileController controller = new FileController(sysBookService, chapterMapper, authContextService);
        ReflectionTestUtils.setField(controller, "uploadPath", tempDir.toString());

        ResponseEntity<Resource> response = controller.getFile("shared.txt", null, request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }
}
