package com.example.reading.controller;

import com.example.reading.common.Result;
import com.example.reading.entity.SysParagraphComment;
import com.example.reading.mapper.SysParagraphCommentMapper;
import com.example.reading.service.AuthContextService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ParagraphCommentControllerTest {

    @Mock
    private SysParagraphCommentMapper commentMapper;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private AuthContextService authContextService;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private ParagraphCommentController controller;

    @Test
    void addRejectsMissingBodyWithoutInsert() {
        when(authContextService.currentUserId(request)).thenReturn(4L);

        Result<?> result = controller.add(null, request);

        assertThat(result.getCode()).isEqualTo("500");
        verify(commentMapper, never()).insert(any(SysParagraphComment.class));
    }

    @Test
    void updateRejectsMissingBodyWithoutLoadingComment() {
        when(authContextService.isAdmin(request)).thenReturn(true);

        Result<?> result = controller.update(null, request);

        assertThat(result.getCode()).isEqualTo("500");
        verify(commentMapper, never()).selectById(any());
    }

    @Test
    void toggleLikeRejectsMissingBodyWithoutLoadingComment() {
        when(authContextService.currentUserId(request)).thenReturn(4L);

        Result<?> result = controller.toggleLike(null, request);

        assertThat(result.getCode()).isEqualTo("403");
        verify(commentMapper, never()).selectById(any());
    }

    @Test
    void toggleLikeUsesAtomicIncrementAndReturnsCurrentCountWhenLiking() {
        SysParagraphComment comment = new SysParagraphComment();
        comment.setId(12L);
        comment.setBookId(8L);
        comment.setLikeCount(0);

        when(authContextService.currentUserId(request)).thenReturn(4L);
        when(commentMapper.selectById(12L)).thenReturn(comment);
        when(authContextService.canViewBook(8L, request)).thenReturn(true);
        when(jdbcTemplate.update(
                eq("DELETE FROM sys_paragraph_like WHERE comment_id = ? AND user_id = ?"),
                eq(12L),
                eq(4L))).thenReturn(0);
        when(jdbcTemplate.update(
                eq("INSERT INTO sys_paragraph_like (comment_id, user_id) VALUES (?, ?)"),
                eq(12L),
                eq(4L))).thenReturn(1);
        when(jdbcTemplate.queryForObject(
                eq("SELECT IFNULL(like_count, 0) FROM sys_paragraph_comment WHERE id = ?"),
                eq(Integer.class),
                eq(12L))).thenReturn(1);

        Result<?> result = controller.toggleLike(Map.of("commentId", 12L), request);

        assertThat(result.getCode()).isEqualTo("200");
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertThat(data).containsEntry("likeCount", 1).containsEntry("isLiked", true);
        verify(jdbcTemplate).update(
                "UPDATE sys_paragraph_comment SET like_count = IFNULL(like_count, 0) + 1 WHERE id = ?",
                12L);
    }
}
