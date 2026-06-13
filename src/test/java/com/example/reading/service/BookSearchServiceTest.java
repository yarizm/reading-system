package com.example.reading.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.reading.entity.EsBookDoc;
import com.example.reading.entity.SysBook;
import com.example.reading.entity.SysChapter;
import com.example.reading.mapper.SysBookMapper;
import com.example.reading.mapper.SysChapterMapper;
import com.example.reading.repository.EsBookRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.SearchHitsImpl;
import org.springframework.data.elasticsearch.core.TotalHitsRelation;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;

import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookSearchServiceTest {

    @Mock
    private EsBookRepository esBookRepository;

    @Mock
    private ElasticsearchOperations elasticsearchOperations;

    @Mock
    private SysBookMapper sysBookMapper;

    @Mock
    private SysChapterMapper chapterMapper;

    @InjectMocks
    private BookSearchService service;

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void syncAllBooksToEsBatchLoadsChaptersForAllBooks() {
        SysBook firstBook = book(1L, "First");
        SysBook secondBook = book(2L, "Second");
        when(sysBookMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(firstBook, secondBook));
        when(chapterMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(
                chapter(1L, 0, "A"),
                chapter(1L, 1, "B"),
                chapter(2L, 0, "C")
        ));

        int synced = service.syncAllBooksToEs();

        assertThat(synced).isEqualTo(2);
        verify(chapterMapper).selectList(any(QueryWrapper.class));
        verify(esBookRepository).deleteAll();

        ArgumentCaptor<Iterable> docsCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(esBookRepository).saveAll(docsCaptor.capture());
        List<EsBookDoc> docs = StreamSupport.stream(docsCaptor.getValue().spliterator(), false)
                .map(EsBookDoc.class::cast)
                .toList();

        assertThat(docs).extracting(EsBookDoc::getId).containsExactly(1L, 2L);
        assertThat(docs.get(0).getChapterContents()).isEqualTo("A\nB");
        assertThat(docs.get(1).getChapterContents()).isEqualTo("C");
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchAddsElasticsearchHitsToReturnedRecords() {
        EsBookDoc doc = new EsBookDoc();
        doc.setId(10L);
        doc.setTitle("Searchable");
        doc.setAuthor("Author");
        doc.setDescription("Description");
        doc.setCategory("Novel");
        doc.setCoverUrl("/files/cover.jpg");

        SearchHit<EsBookDoc> hit = new SearchHit<>(
                "es_book",
                "10",
                null,
                1.5f,
                new Object[0],
                Map.of(),
                Map.of(),
                null,
                null,
                List.of(),
                doc
        );
        SearchHits<EsBookDoc> hits = new SearchHitsImpl<>(
                1,
                TotalHitsRelation.EQUAL_TO,
                1.5f,
                null,
                null,
                List.of(hit),
                null,
                null
        );
        when(elasticsearchOperations.search(any(NativeQuery.class), eq(EsBookDoc.class))).thenReturn(hits);

        Map<String, Object> result = service.search("search", "", 1, 10);

        List<Map<String, Object>> records = (List<Map<String, Object>>) result.get("records");
        assertThat(records).hasSize(1);
        assertThat(records.get(0))
                .containsEntry("id", 10L)
                .containsEntry("title", "Searchable")
                .containsEntry("author", "Author")
                .containsEntry("description", "Description")
                .containsEntry("category", "Novel")
                .containsEntry("coverUrl", "/files/cover.jpg")
                .containsEntry("score", 1.5f);
        assertThat(result).containsEntry("total", 1L);
    }

    @Test
    void searchNormalizesInvalidPaginationBeforeBuildingEsQuery() {
        SearchHits<EsBookDoc> hits = new SearchHitsImpl<>(
                0,
                TotalHitsRelation.EQUAL_TO,
                0.0f,
                null,
                null,
                List.of(),
                null,
                null
        );
        when(elasticsearchOperations.search(any(NativeQuery.class), eq(EsBookDoc.class))).thenReturn(hits);

        Map<String, Object> result = service.search("search", "", 0, 0);

        ArgumentCaptor<NativeQuery> queryCaptor = ArgumentCaptor.forClass(NativeQuery.class);
        verify(elasticsearchOperations).search(queryCaptor.capture(), eq(EsBookDoc.class));
        assertThat(queryCaptor.getValue().getPageable().getPageNumber()).isZero();
        assertThat(queryCaptor.getValue().getPageable().getPageSize()).isEqualTo(1);
        assertThat(result).containsEntry("total", 0L);
    }

    private static SysBook book(Long id, String title) {
        SysBook book = new SysBook();
        book.setId(id);
        book.setTitle(title);
        return book;
    }

    private static SysChapter chapter(Long bookId, Integer sort, String content) {
        SysChapter chapter = new SysChapter();
        chapter.setBookId(bookId);
        chapter.setSort(sort);
        chapter.setContent(content);
        return chapter;
    }
}
