package com.example.reading.controller;

import com.example.reading.common.Result;
import com.example.reading.entity.SysComment;
import com.example.reading.mapper.SysCommentMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 书籍评论控制器
 * 提供书籍评论的查询（树形结构）、发表、点赞/取消点赞、删除功能。
 */
@RestController
@RequestMapping("/comment")
public class SysCommentController {

    @Autowired
    private SysCommentMapper commentMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 获取评论列表（组装为两层树形结构：顶级评论 → 回复列表） */
    @GetMapping("/list/{bookId}")
    public Result<List<SysComment>> list(@PathVariable Long bookId, @RequestParam(required = false) Long userId) {
        List<SysComment> allComments = commentMapper.selectByBookId(bookId, userId);

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
                if (parent != null) {
                    parent.getChildren().add(c);
                } else {
                    rootComments.add(c);
                }
            }
        }

        return Result.success(rootComments);
    }

    /** 发表评论或回复（子评论强制评分为 0，顶级评论默认评分 5） */
    @PostMapping("/add")
    public Result<?> add(@RequestBody SysComment comment) {
        if (comment.getUserId() == null || comment.getBookId() == null) {
            return Result.error("500", "参数错误");
        }

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

    /** 切换评论点赞状态 */
    @PostMapping("/like")
    @Transactional
    public Result<?> toggleLike(@RequestBody Map<String, Long> params) {
        Long commentId = params.get("commentId");
        Long userId = params.get("userId");

        String checkSql = "SELECT count(*) FROM sys_comment_like WHERE comment_id = ? AND user_id = ?";
        Integer count = jdbcTemplate.queryForObject(checkSql, Integer.class, commentId, userId);

        SysComment comment = commentMapper.selectById(commentId);
        if (comment == null) return Result.error("404", "评论不存在");

        boolean isLiked;
        if (count > 0) {
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

    /** 删除评论（同时清除关联的点赞记录） */
    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        commentMapper.deleteById(id);
        jdbcTemplate.update("DELETE FROM sys_comment_like WHERE comment_id = ?", id);
        return Result.success();
    }

    /** 获取某个用户的所有书籍评论（管理员用） */
    @GetMapping("/user/{userId}")
    public Result<List<SysComment>> getUserComments(@PathVariable Long userId) {
        return Result.success(commentMapper.selectUserAllComments(userId));
    }

    /** 修改书籍评论内容（管理员用） */
    @PutMapping("/update")
    public Result<?> update(@RequestBody SysComment comment) {
        if (comment.getId() == null || comment.getContent() == null) {
            return Result.error("500", "参数错误");
        }
        SysComment exist = commentMapper.selectById(comment.getId());
        if (exist == null) {
            return Result.error("404", "评论不存在");
        }
        exist.setContent(comment.getContent());
        commentMapper.updateById(exist);
        return Result.success("修改成功");
    }
}