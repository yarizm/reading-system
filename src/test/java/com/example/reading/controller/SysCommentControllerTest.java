package com.example.reading.controller;

import com.example.reading.common.Result;
import com.example.reading.entity.SysComment;
import com.example.reading.mapper.SysCommentMapper;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SysCommentControllerTest {

    @Mock
    private SysCommentMapper commentMapper;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private AuthContextService authContextService;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private SysCommentController controller;

    @Test
    void toggleLikeUsesAtomicDecrementAndReturnsCurrentCountWhenUnliking() {
        SysComment comment = new SysComment();
        comment.setId(9L);
        comment.setBookId(7L);
        comment.setLikeCount(1);

        when(authContextService.currentUserId(request)).thenReturn(3L);
        when(commentMapper.selectById(9L)).thenReturn(comment);
        when(authContextService.canViewBook(7L, request)).thenReturn(true);
        when(jdbcTemplate.update(
                eq("DELETE FROM sys_comment_like WHERE comment_id = ? AND user_id = ?"),
                eq(9L),
                eq(3L))).thenReturn(1);
        when(jdbcTemplate.queryForObject(
                eq("SELECT IFNULL(like_count, 0) FROM sys_comment WHERE id = ?"),
                eq(Integer.class),
                eq(9L))).thenReturn(0);

        Result<?> result = controller.toggleLike(Map.of("commentId", 9L), request);

        assertThat(result.getCode()).isEqualTo("200");
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertThat(data).containsEntry("likeCount", 0).containsEntry("isLiked", false);
        verify(jdbcTemplate).update(
                "UPDATE sys_comment SET like_count = GREATEST(IFNULL(like_count, 0) - 1, 0) WHERE id = ?",
                9L);
    }
}
