package com.example.reading.controller;

import com.example.reading.common.Result;
import com.example.reading.entity.SysParagraphComment;
import com.example.reading.entity.SysUser;
import com.example.reading.mapper.SysParagraphCommentMapper;
import com.example.reading.service.ISysUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 段落级评论控制器
 * 提供基于书籍章节段落定位的精细化评论功能，含发表、列表查询（带点赞状态）、
 * 点赞/取消点赞、删除及"我的评论"查询。
 */
@RestController
@RequestMapping("/paragraphComment")
public class ParagraphCommentController {

    @Autowired
    private SysParagraphCommentMapper commentMapper;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Autowired
    private ISysUserService userService;

    /** 获取指定段落的评论列表（含当前用户的点赞状态） */
    @GetMapping("/list/{bookId}/{chapterIndex}/{paragraphIndex}")
    public Result<List<SysParagraphComment>> list(
            @PathVariable Long bookId,
            @PathVariable Integer chapterIndex,
            @PathVariable Integer paragraphIndex,
            @RequestParam(required = false) Long currentUserId) {
        List<SysParagraphComment> list = commentMapper.selectByPositionWithLike(bookId, chapterIndex, paragraphIndex, currentUserId);
        return Result.success(list);
    }

    /** 删除段落评论（本人或管理员可操作） */
    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id, @RequestParam Long userId) {
        SysParagraphComment comment = commentMapper.selectById(id);
        if (comment == null) return Result.error("404", "评论不存在");

        if (!comment.getUserId().equals(userId)) {
            SysUser user = userService.getById(userId);
            if (user == null || user.getRole() != 1) {
                return Result.error("403", "无权删除");
            }
        }

        commentMapper.deleteById(id);
        return Result.success();
    }

    /** 切换段落评论的点赞状态 */
    @PostMapping("/like")
    @Transactional
    public Result<?> toggleLike(@RequestBody Map<String, Long> params) {
        Long commentId = params.get("commentId");
        Long userId = params.get("userId");

        String checkSql = "SELECT count(*) FROM sys_paragraph_like WHERE comment_id = ? AND user_id = ?";
        Integer count = jdbcTemplate.queryForObject(checkSql, Integer.class, commentId, userId);

        SysParagraphComment comment = commentMapper.selectById(commentId);
        if (comment == null) return Result.error("404", "评论不存在");

        boolean isLikedNow;
        if (count > 0) {
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

    /** 获取当前用户在指定书籍下的所有段落评论 */
    @GetMapping("/my/{bookId}/{userId}")
    public Result<List<SysParagraphComment>> myComments(@PathVariable Long bookId, @PathVariable Long userId) {
        return Result.success(commentMapper.selectUserComments(userId, bookId));
    }

    /** 发表段落评论 */
    @PostMapping("/add")
    public Result<?> add(@RequestBody SysParagraphComment comment) {
        if (comment.getUserId() == null || comment.getContent() == null) {
            return Result.error("500", "参数不完整");
        }
        if (comment.getCreateTime() == null) {
            comment.setCreateTime(LocalDateTime.now());
        }
        commentMapper.insert(comment);
        return Result.success("评论成功");
    }

    /** 获取某个用户的所有段落评论（管理员用） */
    @GetMapping("/user/{userId}")
    public Result<List<SysParagraphComment>> getUserComments(@PathVariable Long userId) {
        return Result.success(commentMapper.selectAllUserParagraphComments(userId));
    }

    /** 修改段落评论内容（管理员用） */
    @PutMapping("/update")
    public Result<?> update(@RequestBody SysParagraphComment comment) {
        if (comment.getId() == null || comment.getContent() == null) {
            return Result.error("500", "参数错误");
        }
        SysParagraphComment exist = commentMapper.selectById(comment.getId());
        if (exist == null) {
            return Result.error("404", "评论不存在");
        }
        exist.setContent(comment.getContent());
        commentMapper.updateById(exist);
        return Result.success("修改成功");
    }
}