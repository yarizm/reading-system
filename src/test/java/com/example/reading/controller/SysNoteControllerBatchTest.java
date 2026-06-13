package com.example.reading.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.reading.common.Result;
import com.example.reading.entity.SysBook;
import com.example.reading.entity.SysNote;
import com.example.reading.service.AuthContextService;
import com.example.reading.service.INoteReviewService;
import com.example.reading.service.INoteTagService;
import com.example.reading.service.ISysBookService;
import com.example.reading.service.ISysNoteService;
import com.example.reading.service.NoteEsService;
import com.example.reading.service.NoteTagViewService;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SysNoteControllerBatchTest {

    @Mock
    private ISysNoteService sysNoteService;

    @Mock
    private AuthContextService authContextService;

    @Mock
    private ISysBookService sysBookService;

    @Mock
    private INoteTagService noteTagService;

    @Mock
    private NoteTagViewService noteTagViewService;

    @Mock
    private NoteEsService noteEsService;

    @Mock
    private INoteReviewService noteReviewService;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private SysNoteController controller;

    @Test
    void addRejectsMissingBodyWithoutSavingOrSyncing() {
        when(authContextService.currentUserId(request)).thenReturn(7L);

        Result<?> result = controller.add(null, request);

        assertThat(result.getCode()).isEqualTo("403");
        verify(sysNoteService, never()).save(any(SysNote.class));
        verify(noteEsService, never()).syncNoteToEs(any(SysNote.class));
        verify(noteReviewService, never()).autoAddToReview(anyLong(), anyLong());
    }

    @Test
    void globalListBatchLoadsBooksAndTagsForPageRecords() {
        SysNote note = new SysNote();
        note.setId(11L);
        note.setUserId(7L);
        note.setBookId(3L);
        note.setSelectedText("selected text");
        note.setContent("note content");
        note.setCreateTime(LocalDateTime.now());

        SysBook book = new SysBook();
        book.setId(3L);
        book.setTitle("Batch Book");
        book.setAuthor("Batch Author");
        book.setCoverUrl("/files/cover.png");

        when(authContextService.currentUserId(request)).thenReturn(7L);
        when(sysNoteService.count(any(QueryWrapper.class))).thenReturn(1L);
        when(sysNoteService.list(any(QueryWrapper.class))).thenReturn(List.of(note));
        when(sysBookService.listByIds(any())).thenReturn(List.of(book));
        when(noteTagViewService.listTagInfoByNoteIds(any()))
                .thenReturn(Map.of(11L, List.of(tagInfo(5L, "Key Point", "#e74c3c"))));

        Result<Map<String, Object>> result = controller.globalList(
                null, null, null, null, null, 1, 20, request);

        assertThat(result.getCode()).isEqualTo("200");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> records = (List<Map<String, Object>>) result.getData().get("records");
        assertThat(records).hasSize(1);
        assertThat(records.get(0)).containsEntry("bookTitle", "Batch Book");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tags = (List<Map<String, Object>>) records.get(0).get("tags");
        assertThat(tags.get(0)).containsEntry("name", "Key Point");

        verify(sysBookService, never()).getById(anyLong());
        verify(noteTagService, never()).getTagIdsByNoteId(anyLong());
    }

    private static Map<String, Object> tagInfo(Long id, String name, String color) {
        Map<String, Object> tag = new java.util.HashMap<>();
        tag.put("id", id);
        tag.put("name", name);
        tag.put("color", color);
        return tag;
    }
}
