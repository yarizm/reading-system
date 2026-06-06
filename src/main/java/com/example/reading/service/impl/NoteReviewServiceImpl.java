package com.example.reading.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.reading.entity.NoteReview;
import com.example.reading.mapper.NoteReviewMapper;
import com.example.reading.service.INoteReviewService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class NoteReviewServiceImpl extends ServiceImpl<NoteReviewMapper, NoteReview> implements INoteReviewService {

    @Override
    @Transactional
    public void autoAddToReview(Long userId, Long noteId) {
        QueryWrapper<NoteReview> check = new QueryWrapper<>();
        check.eq("user_id", userId).eq("note_id", noteId);
        if (count(check) > 0) return;

        NoteReview review = new NoteReview();
        review.setUserId(userId);
        review.setNoteId(noteId);
        review.setEaseFactor(2.5f);
        review.setIntervalDays(1);
        review.setRepetitions(0);
        review.setNextReviewDate(LocalDate.now().plusDays(1));
        save(review);
    }

    @Override
    public List<NoteReview> getTodayReviews(Long userId) {
        String validNoteIdsSql = validNoteIdsSubquery(userId);
        QueryWrapper<NoteReview> query = new QueryWrapper<>();
        query.eq("user_id", userId)
             .le("next_review_date", LocalDate.now())
             .inSql("note_id", validNoteIdsSql)
             .orderByAsc("next_review_date")
             .last("LIMIT 20");
        return list(query);
    }

    @Override
    public Map<String, Object> rate(Long userId, Long noteId, int score) {
        QueryWrapper<NoteReview> query = new QueryWrapper<>();
        query.eq("user_id", userId).eq("note_id", noteId);
        NoteReview review = getOne(query);

        if (review == null) {
            review = new NoteReview();
            review.setUserId(userId);
            review.setNoteId(noteId);
            review.setEaseFactor(2.5f);
            review.setIntervalDays(1);
            review.setRepetitions(0);
        }

        // SM-2 algorithm
        if (score < 3) {
            review.setRepetitions(0);
            review.setIntervalDays(1);
        } else {
            if (review.getRepetitions() == 0) {
                review.setIntervalDays(1);
            } else if (review.getRepetitions() == 1) {
                review.setIntervalDays(6);
            } else {
                long calculated = Math.round(review.getIntervalDays() * (double) review.getEaseFactor());
                review.setIntervalDays((int) Math.min(calculated, 36500)); // 上限 100 年，防止 int 溢出
            }
            review.setRepetitions(review.getRepetitions() + 1);
        }

        double ef = review.getEaseFactor() + (0.1 - (5 - score) * (0.08 + (5 - score) * 0.02));
        review.setEaseFactor((float) Math.max(1.3, ef));

        review.setNextReviewDate(LocalDate.now().plusDays(review.getIntervalDays()));
        review.setLastReviewTime(LocalDateTime.now());

        if (review.getId() == null) {
            save(review);
        } else {
            updateById(review);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("nextReviewDate", review.getNextReviewDate().toString());
        result.put("intervalDays", review.getIntervalDays());
        return result;
    }

    @Override
    public Map<String, Object> getStats(Long userId) {
        Map<String, Object> stats = new HashMap<>();

        // 只统计笔记仍然存在的 review 记录，排除已删除笔记的孤儿数据
        String validNoteIdsSql = validNoteIdsSubquery(userId);

        QueryWrapper<NoteReview> reviewQuery = new QueryWrapper<>();
        reviewQuery.eq("user_id", userId).inSql("note_id", validNoteIdsSql);
        stats.put("reviewNotes", count(reviewQuery));

        QueryWrapper<NoteReview> pendingQuery = new QueryWrapper<>();
        pendingQuery.eq("user_id", userId).le("next_review_date", LocalDate.now()).inSql("note_id", validNoteIdsSql);
        stats.put("todayPending", count(pendingQuery));

        stats.put("streakDays", calculateStreak(userId));
        return stats;
    }

    @Override
    public void removeFromReview(Long userId, Long noteId) {
        QueryWrapper<NoteReview> query = new QueryWrapper<>();
        query.eq("user_id", userId).eq("note_id", noteId);
        remove(query);
    }

    @Override
    public Set<Long> getReviewedNoteIds(Long userId) {
        String validNoteIdsSql = validNoteIdsSubquery(userId);
        QueryWrapper<NoteReview> query = new QueryWrapper<>();
        query.eq("user_id", userId).inSql("note_id", validNoteIdsSql).select("note_id");
        return list(query).stream().map(NoteReview::getNoteId).collect(Collectors.toSet());
    }

    /**
     * 构造有效的笔记 ID 子查询，排除已删除笔记的孤儿 review 记录
     */
    private String validNoteIdsSubquery(Long userId) {
        return "SELECT id FROM sys_note WHERE user_id = " + userId;
    }

    private int calculateStreak(Long userId) {
        QueryWrapper<NoteReview> query = new QueryWrapper<>();
        query.eq("user_id", userId)
             .isNotNull("last_review_time")
             .ge("last_review_time", LocalDate.now().minusDays(400).atStartOfDay())
             .select("last_review_time");
        List<NoteReview> reviews = list(query);

        Set<LocalDate> reviewDates = reviews.stream()
            .filter(r -> r.getLastReviewTime() != null)
            .map(r -> r.getLastReviewTime().toLocalDate())
            .collect(Collectors.toSet());

        int streak = 0;
        LocalDate checkDate = LocalDate.now();
        while (reviewDates.contains(checkDate)) {
            streak++;
            checkDate = checkDate.minusDays(1);
        }
        return streak;
    }
}
