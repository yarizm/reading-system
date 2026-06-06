package com.example.reading.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.reading.entity.NoteReview;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface INoteReviewService extends IService<NoteReview> {
    void autoAddToReview(Long userId, Long noteId);
    List<NoteReview> getTodayReviews(Long userId);
    Map<String, Object> rate(Long userId, Long noteId, int score);
    Map<String, Object> getStats(Long userId);
    void removeFromReview(Long userId, Long noteId);
    Set<Long> getReviewedNoteIds(Long userId);
}
