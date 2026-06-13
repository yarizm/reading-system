package com.example.reading.controller;

import com.example.reading.common.Result;
import com.example.reading.entity.NoteRelation;
import com.example.reading.entity.SysNote;
import com.example.reading.service.AuthContextService;
import com.example.reading.service.INoteRelationService;
import com.example.reading.service.ISysNoteService;
import com.example.reading.service.NoteTagViewService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoteRelationControllerBatchTest {

    @Mock
    private INoteRelationService noteRelationService;

    @Mock
    private ISysNoteService sysNoteService;

    @Mock
    private NoteTagViewService noteTagViewService;

    @Mock
    private AuthContextService authContextService;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private NoteRelationController controller;

    @Test
    void listBatchLoadsRelatedNotesAndTags() {
        SysNote sourceNote = note(1L, 10L, "source");
        SysNote relatedTwo = note(2L, 10L, "two");
        SysNote relatedThree = note(3L, 10L, "three");
        NoteRelation relationA = relation(101L, 1L, 2L);
        NoteRelation relationB = relation(102L, 1L, 3L);
        when(authContextService.currentUserId(request)).thenReturn(10L);
        when(sysNoteService.getById(1L)).thenReturn(sourceNote);
        when(noteRelationService.getRelationsByNoteId(1L)).thenReturn(List.of(relationA, relationB));
        when(sysNoteService.listByIds(argThat(ids -> containsExactlyIds(ids, 2L, 3L))))
                .thenReturn(List.of(relatedTwo, relatedThree));
        when(noteTagViewService.listTagInfoByNoteIds(argThat(ids -> containsExactlyIds(ids, 2L, 3L))))
                .thenReturn(Map.of(
                        2L, List.of(tagInfo(20L, "important", "#f00")),
                        3L, List.of(tagInfo(30L, "idea", "#0f0"))
                ));

        Result<List<Map<String, Object>>> result = controller.list(1L, request);

        assertThat(result.getCode()).isEqualTo("200");
        assertThat(result.getData()).hasSize(2);
        assertThat(result.getData().get(0)).containsEntry("relationId", 101L);

        @SuppressWarnings("unchecked")
        Map<String, Object> relatedNote = (Map<String, Object>) result.getData().get(0).get("relatedNote");
        assertThat(relatedNote)
                .containsEntry("id", 2L)
                .containsEntry("content", "two")
                .containsEntry("selectedText", "quote-2");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tags = (List<Map<String, Object>>) relatedNote.get("tags");
        assertThat(tags).hasSize(1);
        assertThat(tags.get(0))
                .containsEntry("id", 20L)
                .containsEntry("name", "important")
                .containsEntry("color", "#f00");

        verify(sysNoteService, never()).getById(2L);
        verify(sysNoteService, never()).getById(3L);
        verify(noteTagViewService).listTagInfoByNoteIds(argThat(ids -> containsExactlyIds(ids, 2L, 3L)));
    }

    @Test
    void createRejectsMissingBodyWithoutLoadingNotes() {
        when(authContextService.currentUserId(request)).thenReturn(10L);

        Result<?> result = controller.create(null, request);

        assertThat(result.getCode()).isEqualTo("400");
        verify(sysNoteService, never()).getById(any());
        verify(noteRelationService, never()).createRelation(any(), any());
    }

    @Test
    void createRejectsNoteWithoutOwnerWithoutThrowing() {
        SysNote ownedNote = note(1L, 10L, "owned");
        SysNote ownerlessNote = note(2L, null, "ownerless");

        when(authContextService.currentUserId(request)).thenReturn(10L);
        when(sysNoteService.getById(1L)).thenReturn(ownedNote);
        when(sysNoteService.getById(2L)).thenReturn(ownerlessNote);

        Result<?> result = controller.create(Map.of("noteId1", 1L, "noteId2", 2L), request);

        assertThat(result.getCode()).isEqualTo("403");
        verify(noteRelationService, never()).createRelation(any(), any());
    }

    @Test
    void listSkipsMalformedRelationsWithoutThrowing() {
        SysNote sourceNote = note(1L, 10L, "source");
        NoteRelation malformed = relation(101L, null, 2L);

        when(authContextService.currentUserId(request)).thenReturn(10L);
        when(sysNoteService.getById(1L)).thenReturn(sourceNote);
        when(noteRelationService.getRelationsByNoteId(1L)).thenReturn(List.of(malformed));
        when(noteTagViewService.listTagInfoByNoteIds(argThat(Collection::isEmpty))).thenReturn(Map.of());

        Result<List<Map<String, Object>>> result = controller.list(1L, request);

        assertThat(result.getCode()).isEqualTo("200");
        assertThat(result.getData()).isEmpty();
        verify(sysNoteService, never()).listByIds(any());
    }

    @Test
    void deleteRequiresBothRelatedNotesToBelongToCurrentUser() {
        NoteRelation relation = relation(101L, 1L, 2L);
        SysNote ownedNote = note(1L, 10L, "owned");
        SysNote otherUserNote = note(2L, 11L, "other");

        when(authContextService.currentUserId(request)).thenReturn(10L);
        when(noteRelationService.getById(101L)).thenReturn(relation);
        when(sysNoteService.getById(1L)).thenReturn(ownedNote);
        when(sysNoteService.getById(2L)).thenReturn(otherUserNote);

        Result<?> result = controller.delete(101L, request);

        assertThat(result.getCode()).isEqualTo("403");
        verify(noteRelationService, never()).removeById(101L);
    }

    private static boolean containsExactlyIds(Collection<?> values, Long... expected) {
        return values != null && values.size() == expected.length && values.containsAll(List.of(expected));
    }

    private static SysNote note(Long id, Long userId, String content) {
        SysNote note = new SysNote();
        note.setId(id);
        note.setUserId(userId);
        note.setContent(content);
        note.setSelectedText("quote-" + id);
        note.setCreateTime(LocalDateTime.of(2026, 6, 9, 9, id.intValue()));
        return note;
    }

    private static NoteRelation relation(Long id, Long noteId1, Long noteId2) {
        NoteRelation relation = new NoteRelation();
        relation.setId(id);
        relation.setNoteId1(noteId1);
        relation.setNoteId2(noteId2);
        return relation;
    }

    private static Map<String, Object> tagInfo(Long id, String name, String color) {
        Map<String, Object> tag = new java.util.HashMap<>();
        tag.put("id", id);
        tag.put("name", name);
        tag.put("color", color);
        return tag;
    }
}
