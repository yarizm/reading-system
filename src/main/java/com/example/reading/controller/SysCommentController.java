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
import java.util.stream.Collectors;

@RestController
@RequestMapping("/comment")
public class SysCommentController {

    @Autowired
    private SysCommentMapper commentMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // 获取评论列表 (树形结构)
    @GetMapping("/list/{bookId}")
    public Result<List<SysComment>> list(@PathVariable Long bookId, @RequestParam(required = false) Long userId) {
        // 1. 查出所有评论
        List<SysComment> allComments = commentMapper.selectByBookId(bookId, userId);

        // 2. 组装成树形结构 (只支持二级：顶级评论 -> 回复列表)
        List<SysComment> rootComments = new ArrayList<>();
        Map<Long, SysComment> map = new HashMap<>();

        // 先把所有评论放 map 里方便查找
        for (SysComment c : allComments) {
            c.setChildren(new ArrayList<>()); // 初始化子列表
            map.put(c.getId(), c);
        }

        // 遍历分类
        for (SysComment c : allComments) {
            if (c.getParentId() == 0) {
                // 顶级评论
                rootComments.add(c);
            } else {
                // 子评论，找到它的父级 (这里简化逻辑：无论回复谁，只要 parent_id 不是 0，都挂在顶级评论下展示，或者挂在直接父级下)
                // 为了 UI 简单，通常电子书评论区采用 "两层结构"：
                // 顶级评论 A
                //   - 子评论 B (回复 A)
                //   - 子评论 C (回复 B) -> 也可以直接展示在 A 下面

                // 这里我们尝试找直接父级，如果找不到就算了
                SysComment parent = map.get(c.getParentId());
                if (parent != null) {
                    parent.getChildren().add(c);
                } else {
                    // 孤儿评论（父级可能被删了），作为顶级展示
                    rootComments.add(c);
                }
            }
        }

        // 可选：对 rootComments 里的子列表按时间排序
        return Result.success(rootComments);
    }

    // 发表评论 / 回复
    @PostMapping("/add")
    public Result<?> add(@RequestBody SysComment comment) {
        if (comment.getUserId() == null || comment.getBookId() == null) {
            return Result.error("500", "参数错误");
        }

        // === 核心修改 ===
        if (comment.getParentId() != null && comment.getParentId() != 0) {
            // 如果是子评论，强制设为 0 (代表无评分)
            // 注意：不能设为 null，否则数据库会填入默认值 5
            comment.setRating(0);
        } else {
            // 顶级评论，如果没传分，默认给 5
            comment.setParentId(0L);
            if (comment.getRating() == null) comment.setRating(5);
        }

        comment.setCreateTime(LocalDateTime.now());
        comment.setLikeCount(0);
        commentMapper.insert(comment);
        return Result.success();
    }

    // 点赞 / 取消点赞
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
            // 取消
            jdbcTemplate.update("DELETE FROM sys_comment_like WHERE comment_id = ? AND user_id = ?", commentId, userId);
            comment.setLikeCount(Math.max(0, comment.getLikeCount() - 1));
            isLiked = false;
        } else {
            // 点赞
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

    // 删除评论
    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        // 实际项目要校验权限
        commentMapper.deleteById(id);
        // 同时删除相关的赞
        jdbcTemplate.update("DELETE FROM sys_comment_like WHERE comment_id = ?", id);
        // 可选：同时删除子评论
        // jdbcTemplate.update("DELETE FROM sys_comment WHERE parent_id = ?", id);
        return Result.success();
    }
}