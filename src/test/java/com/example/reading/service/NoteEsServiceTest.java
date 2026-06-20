package com.example.reading.service;

import com.example.reading.entity.EsNoteDoc;
import com.example.reading.repository.EsNoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;

import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class NoteEsServiceTest {

    @Mock
    private EsNoteRepository esNoteRepository;

    @Mock
    private ElasticsearchOperations elasticsearchOperations;

    @Mock
    private ISysBookService sysBookService;

    @Mock
    private INoteTagService noteTagService;

    @Mock
    private ISysTagService tagService;

    @Mock
    private NoteTagViewService noteTagViewService;

    @InjectMocks
    private NoteEsService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "esEnabled", true);
    }

    @Test
    void searchNotesNormalizesInvalidPaginationBeforeBuildingEsQuery() {
        org.springframework.data.elasticsearch.core.SearchHits<EsNoteDoc> hits = mock(org.springframework.data.elasticsearch.core.SearchHits.class);
        when(hits.getTotalHits()).thenReturn(0L);
        when(hits.iterator()).thenReturn(java.util.Collections.emptyIterator());
        when(hits.stream()).thenReturn(java.util.stream.Stream.empty());
        when(elasticsearchOperations.search(any(CriteriaQuery.class), eq(EsNoteDoc.class))).thenReturn(hits);
        when(noteTagViewService.listTagInfoByNoteIds(any())).thenReturn(Map.of());

        Map<String, Object> result = service.searchNotes(7L, "keyword", null, null, 0, 0);

        ArgumentCaptor<CriteriaQuery> queryCaptor = ArgumentCaptor.forClass(CriteriaQuery.class);
        verify(elasticsearchOperations).search(queryCaptor.capture(), eq(EsNoteDoc.class));
        assertThat(queryCaptor.getValue().getPageable().getPageNumber()).isZero();
        assertThat(queryCaptor.getValue().getPageable().getPageSize()).isEqualTo(1);
        assertThat(result).containsEntry("total", 0L);
    }
}
