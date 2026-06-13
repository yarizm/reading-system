package com.example.reading.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.reading.entity.NoteTag;
import com.example.reading.entity.SysTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoteTagViewServiceTest {

    @Mock
    private INoteTagService noteTagService;

    @Mock
    private ISysTagService tagService;

    @Test
    void listTagInfoByNoteIdsBatchLoadsBindingsAndTagDetails() {
        NoteTag noteOneTag = noteTag(1L, 10L);
        NoteTag noteTwoTag = noteTag(2L, 20L);
        SysTag tagTen = tag(10L, "quote", "#123456");
        SysTag tagTwenty = tag(20L, "idea", "#abcdef");
        when(noteTagService.list(any(QueryWrapper.class))).thenReturn(List.of(noteOneTag, noteTwoTag));
        when(tagService.listByIds(argThat(ids -> ids.containsAll(List.of(10L, 20L)))))
                .thenReturn(List.of(tagTen, tagTwenty));

        NoteTagViewService service = new NoteTagViewService(noteTagService, tagService);

        Map<Long, List<Map<String, Object>>> result = service.listTagInfoByNoteIds(List.of(1L, 2L));

        assertThat(result).containsKeys(1L, 2L);
        assertThat(result.get(1L)).hasSize(1);
        assertThat(result.get(1L).get(0))
                .containsEntry("id", 10L)
                .containsEntry("name", "quote")
                .containsEntry("color", "#123456");
        assertThat(result.get(2L)).hasSize(1);
        assertThat(result.get(2L).get(0))
                .containsEntry("id", 20L)
                .containsEntry("name", "idea")
                .containsEntry("color", "#abcdef");
        verify(noteTagService).list(any(QueryWrapper.class));
    }

    @Test
    void listTagInfoByNoteIdsSkipsQueriesWhenInputIsEmpty() {
        NoteTagViewService service = new NoteTagViewService(noteTagService, tagService);

        Map<Long, List<Map<String, Object>>> result = service.listTagInfoByNoteIds(List.of());

        assertThat(result).isEmpty();
        verify(noteTagService, never()).list(any(QueryWrapper.class));
        verify(tagService, never()).listByIds(any());
    }

    private static NoteTag noteTag(Long noteId, Long tagId) {
        NoteTag noteTag = new NoteTag();
        noteTag.setNoteId(noteId);
        noteTag.setTagId(tagId);
        return noteTag;
    }

    private static SysTag tag(Long id, String name, String color) {
        SysTag tag = new SysTag();
        tag.setId(id);
        tag.setName(name);
        tag.setColor(color);
        return tag;
    }
}
