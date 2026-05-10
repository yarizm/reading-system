package com.example.reading.controller;

import com.example.reading.common.Result;
import com.example.reading.entity.SysComment;
import com.example.reading.mapper.SysCommentMapper;
import com.example.reading.service.AuthContextService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
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
        if (currentUserId == null || comment.getBookId() == null) {
            return Result.error("500", "Invalid parameters");
        }
        comment.setUserId(currentUserId);
        if (comment.getParentId() != null && comment.getParentId() != 0) {
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
        Long commentId = params.get("commentId");
        Long userId = authContextService.currentUserId(request);
        if (commentId == null || userId == null) {
            return Result.error("403", "Forbidden");
        }

        SysComment comment = commentMapper.selectById(commentId);
        if (comment == null) return Result.error("404", "Comment not found");

        String checkSql = "SELECT count(*) FROM sys_comment_like WHERE comment_id = ? AND user_id = ?";
        Integer count = jdbcTemplate.queryForObject(checkSql, Integer.class, commentId, userId);

        boolean isLiked;
        if (count != null && count > 0) {
            jdbcTemplate.update("DELETE FROM sys_comment_like WHERE comment_id = ? AND user_id = ?", commentId, userId);
            comment.setLikeCount(Math.max(0, comment.getLikeCount() - 1));
            isLiked = false;
        } else {
            jdbcTemplate.update("INSERT INTO sys_comment_like (comment_id, user_id) VALUES (?, ?)", commentId, userId);
            comment.setLikeCount(comment.getLikeCount() + 1);
            isLiked = true;
        }
        commentMapper.updateById(comment);

        Map<String, Object> res = new HashMap<>();
        res.put("likeCount", comment.getLikeCount());
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
        if (comment.getId() == null || comment.getContent() == null) {
            return Result.error("500", "Invalid parameters");
        }
        SysComment exist = commentMapper.selectById(comment.getId());
        if (exist == null) return Result.error("404", "Comment not found");
        exist.setContent(comment.getContent());
        commentMapper.updateById(exist);
        return Result.success("Updated");
    }
}
