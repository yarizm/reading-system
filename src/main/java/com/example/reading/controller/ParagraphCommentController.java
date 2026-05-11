package com.example.reading.controller;

import com.example.reading.common.Result;
import com.example.reading.entity.SysParagraphComment;
import com.example.reading.mapper.SysParagraphCommentMapper;
import com.example.reading.service.AuthContextService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
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
        Long commentId = params.get("commentId");
        Long userId = authContextService.currentUserId(request);
        if (commentId == null || userId == null) {
            return Result.error("403", "Forbidden");
        }

        SysParagraphComment comment = commentMapper.selectById(commentId);
        if (comment == null) return Result.error("404", "Comment not found");
        if (!authContextService.canViewBook(comment.getBookId(), request)) {
            return Result.error("403", "Forbidden");
        }

        String checkSql = "SELECT count(*) FROM sys_paragraph_like WHERE comment_id = ? AND user_id = ?";
        Integer count = jdbcTemplate.queryForObject(checkSql, Integer.class, commentId, userId);

        boolean isLikedNow;
        if (count != null && count > 0) {
            jdbcTemplate.update("DELETE FROM sys_paragraph_like WHERE comment_id = ? AND user_id = ?", commentId, userId);
            comment.setLikeCount(Math.max(0, comment.getLikeCount() - 1));
            isLikedNow = false;
        } else {
            jdbcTemplate.update("INSERT INTO sys_paragraph_like (comment_id, user_id) VALUES (?, ?)", commentId, userId);
            comment.setLikeCount(comment.getLikeCount() + 1);
            isLikedNow = true;
        }
        commentMapper.updateById(comment);

        Map<String, Object> res = new HashMap<>();
        res.put("likeCount", comment.getLikeCount());
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
        if (currentUserId == null || comment.getBookId() == null || comment.getContent() == null) {
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
        if (comment.getId() == null || comment.getContent() == null) {
            return Result.error("500", "Invalid parameters");
        }
        SysParagraphComment exist = commentMapper.selectById(comment.getId());
        if (exist == null) return Result.error("404", "Comment not found");
        exist.setContent(comment.getContent());
        commentMapper.updateById(exist);
        return Result.success("Updated");
    }
}
