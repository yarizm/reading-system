package com.example.reading.controller;

import com.example.reading.common.Result;
import com.example.reading.entity.SysComment;
import com.example.reading.mapper.SysCommentMapper;
import com.example.reading.service.AuthContextService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/comment")
public class SysCommentController {

    @Autowired
    private SysCommentMapper commentMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuthContextService authContextService;

    @GetMapping("/list/{bookId}")
    public Result<List<SysComment>> list(@PathVariable Long bookId,
                                         @RequestParam(required = false) Long userId,
                                         HttpServletRequest request) {
        if (!authContextService.canViewBook(bookId, request)) {
            return Result.error("403", "Forbidden");
        }
        List<SysComment> allComments = commentMapper.selectByBookId(bookId, authContextService.currentUserId(request));

        List<SysComment> rootComments = new ArrayList<>();
        Map<Long, SysComment> map = new HashMap<>();
        for (SysComment c : allComments) {
            c.setChildren(new ArrayList<>());
            map.put(c.getId(), c);
        }
        for (SysComment c : allComments) {
            if (c.getParentId() == 0) {
                rootComments.add(c);
            } else {
                SysComment parent = map.get(c.getParentId());
                if (parent != null) parent.getChildren().add(c);
                else rootComments.add(c);
            }
        }
        return Result.success(rootComments);
    }

    @PostMapping("/add")
    public Result<?> add(@RequestBody SysComment comment, HttpServletRequest request) {
        Long currentUserId = authContextService.currentUserId(request);
        if (currentUserId == null || comment == null || comment.getBookId() == null) {
            return Result.error("500", "Invalid parameters");
        }
        if (!authContextService.canViewBook(comment.getBookId(), request)) {
            return Result.error("403", "Forbidden");
        }
        comment.setUserId(currentUserId);
        boolean isReply = comment.getParentId() != null && comment.getParentId() != 0;
        if (isReply) {
            SysComment parent = commentMapper.selectById(comment.getParentId());
            if (parent == null || !comment.getBookId().equals(parent.getBookId())) {
                return Result.error("400", "Invalid parent comment");
            }
            comment.setRating(0);
        } else {
            comment.setParentId(0L);
            if (comment.getRating() == null) comment.setRating(5);
        }
        comment.setCreateTime(LocalDateTime.now());
        comment.setLikeCount(0);
        commentMapper.insert(comment);
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

        SysComment comment = commentMapper.selectById(commentId);
        if (comment == null) return Result.error("404", "Comment not found");
        if (!authContextService.canViewBook(comment.getBookId(), request)) {
            return Result.error("403", "Forbidden");
        }

        boolean isLiked;
        int deleted = jdbcTemplate.update("DELETE FROM sys_comment_like WHERE comment_id = ? AND user_id = ?", commentId, userId);
        if (deleted > 0) {
            jdbcTemplate.update("UPDATE sys_comment SET like_count = GREATEST(IFNULL(like_count, 0) - 1, 0) WHERE id = ?", commentId);
            isLiked = false;
        } else {
            try {
                jdbcTemplate.update("INSERT INTO sys_comment_like (comment_id, user_id) VALUES (?, ?)", commentId, userId);
                jdbcTemplate.update("UPDATE sys_comment SET like_count = IFNULL(like_count, 0) + 1 WHERE id = ?", commentId);
            } catch (DuplicateKeyException ignored) {
                // Concurrent request already inserted the like; return the current liked state.
            }
            isLiked = true;
        }
        Integer likeCount = jdbcTemplate.queryForObject(
                "SELECT IFNULL(like_count, 0) FROM sys_comment WHERE id = ?",
                Integer.class,
                commentId);

        Map<String, Object> res = new HashMap<>();
        res.put("likeCount", likeCount == null ? 0 : likeCount);
        res.put("isLiked", isLiked);
        return Result.success(res);
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id, HttpServletRequest request) {
        SysComment comment = commentMapper.selectById(id);
        if (comment == null) return Result.error("404", "Comment not found");
        if (!authContextService.isSelfOrAdmin(comment.getUserId(), request)) {
            return Result.error("403", "Forbidden");
        }
        commentMapper.deleteById(id);
        jdbcTemplate.update("DELETE FROM sys_comment_like WHERE comment_id = ?", id);
        return Result.success();
    }

    @GetMapping("/user/{userId}")
    public Result<List<SysComment>> getUserComments(@PathVariable Long userId, HttpServletRequest request) {
        if (!authContextService.isAdmin(request)) {
            return Result.error("403", "Forbidden");
        }
        return Result.success(commentMapper.selectUserAllComments(userId));
    }

    @PutMapping("/update")
    public Result<?> update(@RequestBody SysComment comment, HttpServletRequest request) {
        if (!authContextService.isAdmin(request)) {
            return Result.error("403", "Forbidden");
        }
        if (comment == null || comment.getId() == null || comment.getContent() == null) {
            return Result.error("500", "Invalid parameters");
        }
        SysComment exist = commentMapper.selectById(comment.getId());
        if (exist == null) return Result.error("404", "Comment not found");
        exist.setContent(comment.getContent());
        commentMapper.updateById(exist);
        return Result.success("Updated");
    }
}
