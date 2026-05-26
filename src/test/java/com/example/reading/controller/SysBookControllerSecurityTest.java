package com.example.reading.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.reading.common.Result;
import com.example.reading.entity.SysBook;
import com.example.reading.mapper.BookReviewRequestMapper;
import com.example.reading.mapper.SysBookMapper;
import com.example.reading.mapper.SysChapterMapper;
import com.example.reading.mapper.SysCommentMapper;
import com.example.reading.mapper.UserBookshelfMapper;
import com.example.reading.service.AuthContextService;
import com.example.reading.service.DifyKnowledgeBaseService;
import com.example.reading.service.IBookRecommendationService;
import com.example.reading.service.ISysBookService;
import com.example.reading.service.ISysUserService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SysBookControllerSecurityTest {

    @Mock
    private ISysBookService sysBookService;

    @Mock
    private SysCommentMapper commentMapper;

    @Mock
    private SysChapterMapper chapterMapper;

    @Mock
    private SysBookMapper sysBookMapper;

    @Mock
    private UserBookshelfMapper bookshelfMapper;

    @Mock
    private IBookRecommendationService bookRecommendationService;

    @Mock
    private ISysUserService sysUserService;

    @Mock
    private BookReviewRequestMapper reviewRequestMapper;

    @Mock
    private DifyKnowledgeBaseService difyKnowledgeBaseService;

    @Mock
    private AuthContextService authContextService;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private SysBookController controller;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(controller, "uploadPath", "C:/tmp/reading-test-files/");
    }

    @Test
    void analyzeRejectsNonUploaderEvenWhenBookIsPublic() {
        SysBook book = new SysBook();
        book.setId(10L);
        book.setUploaderId(100L);
        book.setStatus(2);
        book.setFilePath("/files/book.txt");

        when(authContextService.currentUserId(request)).thenReturn(200L);
        when(sysBookService.getById(10L)).thenReturn(book);
        when(authContextService.isAdmin(200L)).thenReturn(false);

        Result<?> result = controller.analyzeBook(10L, request);

        assertThat(result.getCode()).isEqualTo("403");
    }

    @Test
    void analyzeReturnsValidationErrorForMissingFilePath() {
        SysBook book = new SysBook();
        book.setId(11L);
        book.setUploaderId(100L);

        when(authContextService.currentUserId(request)).thenReturn(100L);
        when(sysBookService.getById(11L)).thenReturn(book);
        when(chapterMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);

        Result<?> result = controller.analyzeBook(11L, request);

        assertThat(result.getCode()).isEqualTo("400");
    }
}
