package com.example.reading.service;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.MultiMatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.reading.entity.EsBookDoc;
import com.example.reading.entity.SysBook;
import com.example.reading.entity.SysChapter;
import com.example.reading.mapper.SysBookMapper;
import com.example.reading.mapper.SysChapterMapper;
import com.example.reading.repository.EsBookRepository;
import com.example.reading.utils.PaginationUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(name = "app.elasticsearch.enabled", havingValue = "true")
public class BookSearchService {

    @Autowired
    private EsBookRepository esBookRepository;

    @Autowired
    private ElasticsearchOperations elasticsearchOperations;

    @Autowired
    private SysBookMapper sysBookMapper;

    @Autowired
    private SysChapterMapper chapterMapper;

    public int syncAllBooksToEs() {
        QueryWrapper<SysBook> query = new QueryWrapper<>();
        query.and(wrapper -> wrapper.eq("status", 2).or().isNull("status"));
        List<SysBook> allBooks = sysBookMapper.selectList(query);
        Map<Long, List<SysChapter>> chaptersByBookId = listChaptersByBookId(
                allBooks.stream()
                        .map(SysBook::getId)
                        .filter(Objects::nonNull)
                        .toList()
        );

        List<EsBookDoc> docs = new ArrayList<>(allBooks.size());
        for (SysBook book : allBooks) {
            docs.add(buildEsDoc(book, chaptersByBookId.getOrDefault(book.getId(), List.of())));
        }

        // Rebuild from public books only, so stale private documents are removed.
        esBookRepository.deleteAll();
        esBookRepository.saveAll(docs);
        return docs.size();
    }

    public void syncOneBookToEs(Long bookId) {
        SysBook book = sysBookMapper.selectById(bookId);
        if (book == null) {
            deleteFromEs(bookId);
            return;
        }
        if (book.getStatus() != null && !Integer.valueOf(2).equals(book.getStatus())) {
            deleteFromEs(bookId);
            return;
        }

        esBookRepository.save(buildEsDoc(book));
    }

    public void deleteFromEs(Long bookId) {
        esBookRepository.deleteById(bookId);
    }

    public Map<String, Object> search(String keyword, String category, int pageNum, int pageSize) {
        int safePageNum = PaginationUtils.pageNum(pageNum);
        int safePageSize = PaginationUtils.pageSize(pageSize);
        BoolQuery.Builder boolBuilder = new BoolQuery.Builder();

        boolBuilder.must(q -> q.multiMatch(MultiMatchQuery.of(mm -> mm
                .query(keyword)
                .fields("title^3", "author^2", "description^2", "chapterContents")
                .type(TextQueryType.BestFields)
                .minimumShouldMatch("1")
        )));

        if (category != null && !category.isEmpty() && !"全部".equals(category)) {
            boolBuilder.filter(f -> f.term(t -> t.field("category").value(category)));
        }

        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.bool(boolBuilder.build()))
                .withPageable(PageRequest.of(safePageNum - 1, safePageSize))
                .build();

        SearchHits<EsBookDoc> hits = elasticsearchOperations.search(query, EsBookDoc.class);

        List<Map<String, Object>> records = new ArrayList<>();
        for (SearchHit<EsBookDoc> hit : hits.getSearchHits()) {
            EsBookDoc doc = hit.getContent();
            Map<String, Object> record = new HashMap<>();
            record.put("id", doc.getId());
            record.put("title", doc.getTitle());
            record.put("author", doc.getAuthor());
            record.put("description", doc.getDescription());
            record.put("category", doc.getCategory());
            record.put("coverUrl", doc.getCoverUrl());
            record.put("score", hit.getScore());
            records.add(record);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("total", hits.getTotalHits());
        return result;
    }

    private EsBookDoc buildEsDoc(SysBook book) {
        QueryWrapper<SysChapter> cq = new QueryWrapper<>();
        cq.eq("book_id", book.getId());
        cq.orderByAsc("sort");
        return buildEsDoc(book, chapterMapper.selectList(cq));
    }

    private EsBookDoc buildEsDoc(SysBook book, List<SysChapter> chapters) {
        EsBookDoc doc = new EsBookDoc();
        doc.setId(book.getId());
        doc.setTitle(book.getTitle());
        doc.setAuthor(book.getAuthor());
        doc.setDescription(book.getDescription());
        doc.setCategory(book.getCategory());
        doc.setCoverUrl(book.getCoverUrl());

        String allContent = chapters.stream()
                .map(SysChapter::getContent)
                .filter(Objects::nonNull)
                .collect(Collectors.joining("\n"));

        if (allContent.length() > 50000) {
            allContent = allContent.substring(0, 50000);
        }

        doc.setChapterContents(allContent);
        return doc;
    }

    private Map<Long, List<SysChapter>> listChaptersByBookId(Collection<Long> bookIds) {
        if (bookIds == null || bookIds.isEmpty()) {
            return Map.of();
        }

        QueryWrapper<SysChapter> cq = new QueryWrapper<>();
        cq.in("book_id", bookIds);
        cq.orderByAsc("book_id");
        cq.orderByAsc("sort");

        return chapterMapper.selectList(cq).stream()
                .filter(chapter -> chapter.getBookId() != null)
                .collect(Collectors.groupingBy(
                        SysChapter::getBookId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
    }
}
