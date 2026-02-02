package com.example.reading.controller;

import com.example.reading.common.Result;
import com.example.reading.entity.SysParagraphComment;
import com.example.reading.mapper.SysParagraphCommentMapper;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired // 需要注入 jdbcTemplate 或专门的 LikeMapper，这里简单演示直接用 mapper 执行 sql
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    // 1. 获取列表 (升级版，带点赞状态)
    @GetMapping("/list/{bookId}/{chapterIndex}/{paragraphIndex}")
    public Result<List<SysParagraphComment>> list(
            @PathVariable Long bookId,
            @PathVariable Integer chapterIndex,
            @PathVariable Integer paragraphIndex,
            @RequestParam(required = false) Long currentUserId) {

        // 如果未登录，currentUserId 传 null，is_liked 也就是 false
        List<SysParagraphComment> list = commentMapper.selectByPositionWithLike(bookId, chapterIndex, paragraphIndex, currentUserId);
        return Result.success(list);
    }

    // 2. 删除评论 (本人或管理员)
    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id, @RequestParam Long userId) {
        SysParagraphComment comment = commentMapper.selectById(id);
        if (comment == null) return Result.error("404", "评论不存在");

        // 简单的权限校验：这里假设 role=1 是管理员。实际项目中应从 Token 获取当前用户信息
        // 这里为了演示，假设前端传来的 userId 是可信的，或者你在拦截器里做了校验
        // 你可以去查一下 SysUser user = userService.getById(userId); if(user.getRole()==1)...

        if (!comment.getUserId().equals(userId)) {
            // 如果不是本人，暂且只允许本人删除 (如果想允许管理员，需查库判断角色)
            return Result.error("403", "无权删除");
        }

        commentMapper.deleteById(id);
        return Result.success();
    }

    // 3. 点赞 / 取消点赞
    @PostMapping("/like")
    @Transactional
    public Result<?> toggleLike(@RequestBody Map<String, Long> params) {
        Long commentId = params.get("commentId");
        Long userId = params.get("userId");

        // 检查是否已点赞
        String checkSql = "SELECT count(*) FROM sys_paragraph_like WHERE comment_id = ? AND user_id = ?";
        Integer count = jdbcTemplate.queryForObject(checkSql, Integer.class, commentId, userId);

        SysParagraphComment comment = commentMapper.selectById(commentId);
        if (comment == null) return Result.error("404", "评论不存在");

        boolean isLikedNow;
        if (count > 0) {
            // 已点赞 -> 取消
            jdbcTemplate.update("DELETE FROM sys_paragraph_like WHERE comment_id = ? AND user_id = ?", commentId, userId);
            comment.setLikeCount(Math.max(0, comment.getLikeCount() - 1));
            isLikedNow = false;
        } else {
            // 未点赞 -> 点赞
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

    // 4. 获取“我的评论”列表
    @GetMapping("/my/{bookId}/{userId}")
    public Result<List<SysParagraphComment>> myComments(@PathVariable Long bookId, @PathVariable Long userId) {
        return Result.success(commentMapper.selectUserComments(userId, bookId));
    }

    /**
     * 发表段落评论
     * POST /api/paragraphComment/add
     */
    @PostMapping("/add")
    public Result<?> add(@RequestBody SysParagraphComment comment) {
        if (comment.getUserId() == null || comment.getContent() == null) {
            return Result.error("500", "参数不完整");
        }

        // 设置时间（如果未配置自动填充）
        if (comment.getCreateTime() == null) {
            comment.setCreateTime(LocalDateTime.now());
        }

        commentMapper.insert(comment);
        return Result.success("评论成功");
    }
}