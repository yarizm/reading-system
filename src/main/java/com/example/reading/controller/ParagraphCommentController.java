package com.example.reading.controller;

import com.example.reading.common.Result;
import com.example.reading.entity.SysParagraphComment;
import com.example.reading.mapper.SysParagraphCommentMapper;
import com.example.reading.service.AuthContextService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/paragraphComment")
public class ParagraphCommentController {

    @Autowired
    private SysParagraphCommentMapper commentMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuthContextService authContextService;

    @GetMapping("/list/{bookId}/{chapterIndex}/{paragraphIndex}")
    public Result<List<SysParagraphComment>> list(@PathVariable Long bookId,
                                                  @PathVariable Integer chapterIndex,
                                                  @PathVariable Integer paragraphIndex,
                                                  @RequestParam(required = false) Long currentUserId,
                                                  HttpServletRequest request) {
        if (!authContextService.canViewBook(bookId, request)) {
            return Result.error("403", "Forbidden");
        }
        return Result.success(commentMapper.selectByPositionWithLike(
                bookId, chapterIndex, paragraphIndex, authContextService.currentUserId(request)));
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id,
                            @RequestParam(required = false) Long userId,
                            HttpServletRequest request) {
        SysParagraphComment comment = commentMapper.selectById(id);
        if (comment == null) return Result.error("404", "Comment not found");
        if (!authContextService.isSelfOrAdmin(comment.getUserId(), request)) {
            return Result.error("403", "Forbidden");
        }
        commentMapper.deleteById(id);
        return Result.success();
    }

    @PostMapping("/like")
    @Transactional
    public Result<?> toggleLike(@RequestBody Map<String, Long> params, HttpServletRequest request) {
        Long commentId = params == null ? null : params.get("commentId");
        Long userId = authContextService.currentUserId(request);
        if (commentId == null || userId == null) {
            return Result.error("403", "Forbidden");
        }

        SysParagraphComment comment = commentMapper.selectById(commentId);
        if (comment == null) return Result.error("404", "Comment not found");
        if (!authContextService.canViewBook(comment.getBookId(), request)) {
            return Result.error("403", "Forbidden");
        }

        boolean isLikedNow;
        int deleted = jdbcTemplate.update("DELETE FROM sys_paragraph_like WHERE comment_id = ? AND user_id = ?", commentId, userId);
        if (deleted > 0) {
            jdbcTemplate.update("UPDATE sys_paragraph_comment SET like_count = GREATEST(IFNULL(like_count, 0) - 1, 0) WHERE id = ?", commentId);
            isLikedNow = false;
        } else {
            try {
                jdbcTemplate.update("INSERT INTO sys_paragraph_like (comment_id, user_id) VALUES (?, ?)", commentId, userId);
                jdbcTemplate.update("UPDATE sys_paragraph_comment SET like_count = IFNULL(like_count, 0) + 1 WHERE id = ?", commentId);
            } catch (DuplicateKeyException ignored) {
                // Concurrent request already inserted the like; return the current liked state.
            }
            isLikedNow = true;
        }
        Integer likeCount = jdbcTemplate.queryForObject(
                "SELECT IFNULL(like_count, 0) FROM sys_paragraph_comment WHERE id = ?",
                Integer.class,
                commentId);

        Map<String, Object> res = new HashMap<>();
        res.put("likeCount", likeCount == null ? 0 : likeCount);
        res.put("isLiked", isLikedNow);
        return Result.success(res);
    }

    @GetMapping("/my/{bookId}/{userId}")
    public Result<List<SysParagraphComment>> myComments(@PathVariable Long bookId,
                                                        @PathVariable Long userId,
                                                        HttpServletRequest request) {
        if (!authContextService.isSelf(userId, request)) {
            return Result.error("403", "Forbidden");
        }
        if (!authContextService.canViewBook(bookId, request)) {
            return Result.error("403", "Forbidden");
        }
        return Result.success(commentMapper.selectUserComments(userId, bookId));
    }

    @PostMapping("/add")
    public Result<?> add(@RequestBody SysParagraphComment comment, HttpServletRequest request) {
        Long currentUserId = authContextService.currentUserId(request);
        if (currentUserId == null || comment == null || comment.getBookId() == null || comment.getContent() == null) {
            return Result.error("500", "Invalid parameters");
        }
        if (!authContextService.canViewBook(comment.getBookId(), request)) {
            return Result.error("403", "Forbidden");
        }
        comment.setUserId(currentUserId);
        if (comment.getCreateTime() == null) comment.setCreateTime(LocalDateTime.now());
        commentMapper.insert(comment);
        return Result.success("Comment created");
    }

    @GetMapping("/user/{userId}")
    public Result<List<SysParagraphComment>> getUserComments(@PathVariable Long userId, HttpServletRequest request) {
        if (!authContextService.isAdmin(request)) {
            return Result.error("403", "Forbidden");
        }
        return Result.success(commentMapper.selectAllUserParagraphComments(userId));
    }

    @PutMapping("/update")
    public Result<?> update(@RequestBody SysParagraphComment comment, HttpServletRequest request) {
        if (!authContextService.isAdmin(request)) {
            return Result.error("403", "Forbidden");
        }
        if (comment == null || comment.getId() == null || comment.getContent() == null) {
            return Result.error("500", "Invalid parameters");
        }
        SysParagraphComment exist = commentMapper.selectById(comment.getId());
        if (exist == null) return Result.error("404", "Comment not found");
        exist.setContent(comment.getContent());
        commentMapper.updateById(exist);
        return Result.success("Updated");
    }
}
