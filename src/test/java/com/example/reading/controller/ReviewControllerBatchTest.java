package com.example.reading.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.reading.common.Result;
import com.example.reading.entity.AiGeneratedContent;
import com.example.reading.entity.NoteReview;
import com.example.reading.entity.SysBook;
import com.example.reading.entity.SysNote;
import com.example.reading.service.AuthContextService;
import com.example.reading.service.IAiGeneratedContentService;
import com.example.reading.service.INoteReviewService;
import com.example.reading.service.ISysBookService;
import com.example.reading.service.ISysNoteService;
import io.github.guoshiqiufeng.dify.chat.DifyChat;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewControllerBatchTest {

    @Mock
    private INoteReviewService noteReviewService;

    @Mock
    private ISysNoteService sysNoteService;

    @Mock
    private ISysBookService sysBookService;

    @Mock
    private IAiGeneratedContentService aiGeneratedContentService;

    @Mock
    private AuthContextService authContextService;

    @Mock
    private DifyChat difyChat;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private ReviewController controller;

    @Test
    void todayBatchLoadsNotesAndBooksForReviewRows() {
        NoteReview review = new NoteReview();
        review.setId(101L);
        review.setNoteId(11L);
        review.setIntervalDays(3);
        review.setRepetitions(2);

        SysNote note = new SysNote();
        note.setId(11L);
        note.setBookId(5L);
        note.setSelectedText("selected text");
        note.setContent("note content");

        SysBook book = new SysBook();
        book.setId(5L);
        book.setTitle("Review Book");

        when(authContextService.currentUserId(request)).thenReturn(7L);
        when(noteReviewService.getTodayReviews(7L)).thenReturn(List.of(review));
        when(sysNoteService.listByIds(any())).thenReturn(List.of(note));
        when(sysBookService.listByIds(any())).thenReturn(List.of(book));

        Result<Map<String, Object>> result = controller.today(request);

        assertThat(result.getCode()).isEqualTo("200");
        assertThat(result.getData()).containsEntry("total", 1);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> reviews = (List<Map<String, Object>>) result.getData().get("reviews");
        @SuppressWarnings("unchecked")
        Map<String, Object> noteInfo = (Map<String, Object>) reviews.get(0).get("note");
        assertThat(noteInfo).containsEntry("bookTitle", "Review Book");

        verify(sysNoteService, never()).getById(anyLong());
        verify(sysBookService, never()).getById(anyLong());
    }

    @Test
    void summaryHistoryBatchLoadsBooksForGeneratedContentRows() {
        AiGeneratedContent content = new AiGeneratedContent();
        content.setId(301L);
        content.setReferenceId(9L);
        content.setContent("summary");
        content.setCreateTime(LocalDateTime.now());

        SysBook book = new SysBook();
        book.setId(9L);
        book.setTitle("Summary Book");

        when(authContextService.currentUserId(request)).thenReturn(7L);
        when(aiGeneratedContentService.list(any(QueryWrapper.class))).thenReturn(List.of(content));
        when(sysBookService.listByIds(any())).thenReturn(List.of(book));

        Result<List<Map<String, Object>>> result = controller.summaryHistory(request);

        assertThat(result.getCode()).isEqualTo("200");
        assertThat(result.getData()).hasSize(1);
        assertThat(result.getData().get(0)).containsEntry("bookTitle", "Summary Book");

        verify(sysBookService, never()).getById(anyLong());
    }

    @Test
    void rateRejectsMissingBodyWithoutLoadingNote() {
        when(authContextService.currentUserId(request)).thenReturn(7L);

        Result<Map<String, Object>> result = controller.rate(null, request);

        assertThat(result.getCode()).isEqualTo("400");
        verify(sysNoteService, never()).getById(anyLong());
    }

    @Test
    void rateRejectsNoteWithoutOwnerWithoutThrowing() {
        SysNote note = new SysNote();
        note.setId(11L);

        when(authContextService.currentUserId(request)).thenReturn(7L);
        when(sysNoteService.getById(11L)).thenReturn(note);

        Result<Map<String, Object>> result = controller.rate(Map.of("noteId", 11L, "score", 3), request);

        assertThat(result.getCode()).isEqualTo("403");
        verify(noteReviewService, never()).rate(anyLong(), anyLong(), anyInt());
    }
}
