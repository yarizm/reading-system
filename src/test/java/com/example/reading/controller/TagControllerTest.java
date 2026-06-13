package com.example.reading.controller;

import com.example.reading.common.Result;
import com.example.reading.entity.SysNote;
import com.example.reading.entity.SysTag;
import com.example.reading.service.AuthContextService;
import com.example.reading.service.INoteTagService;
import com.example.reading.service.ISysNoteService;
import com.example.reading.service.ISysTagService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TagControllerTest {

    @Mock
    private ISysTagService tagService;

    @Mock
    private INoteTagService noteTagService;

    @Mock
    private ISysNoteService sysNoteService;

    @Mock
    private AuthContextService authContextService;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private TagController controller;

    @Test
    void createRejectsMissingBody() {
        when(authContextService.currentUserId(request)).thenReturn(7L);

        Result<?> result = controller.create(null, request);

        assertThat(result.getCode()).isEqualTo("400");
        verify(tagService, never()).save(any(SysTag.class));
    }

    @Test
    void bindRejectsMissingBodyWithoutLoadingNote() {
        when(authContextService.currentUserId(request)).thenReturn(7L);

        Result<?> result = controller.bind(null, request);

        assertThat(result.getCode()).isEqualTo("400");
        verify(sysNoteService, never()).getById(any());
    }

    @Test
    void bindAcceptsNumericStringsAndNumbersForTagIds() {
        SysNote note = new SysNote();
        note.setId(11L);
        note.setUserId(7L);

        when(authContextService.currentUserId(request)).thenReturn(7L);
        when(sysNoteService.getById(11L)).thenReturn(note);

        Result<?> result = controller.bind(
                Map.of("noteId", "11", "tagIds", List.of("1", 2)),
                request
        );

        assertThat(result.getCode()).isEqualTo("200");
        verify(noteTagService).bindTags(eq(11L), eq(List.of(1L, 2L)));
    }

    @Test
    void updateRejectsTagWithoutOwnerWithoutThrowing() {
        SysTag existing = new SysTag();
        existing.setId(5L);

        when(authContextService.currentUserId(request)).thenReturn(7L);
        when(tagService.getById(5L)).thenReturn(existing);

        Result<?> result = controller.update(5L, new SysTag(), request);

        assertThat(result.getCode()).isEqualTo("403");
        verify(tagService, never()).updateById(any(SysTag.class));
    }
}
