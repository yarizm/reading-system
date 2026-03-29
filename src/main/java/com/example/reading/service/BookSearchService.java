package com.example.reading.service;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.MultiMatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import com.example.reading.entity.EsBookDoc;
import com.example.reading.entity.SysBook;
import com.example.reading.entity.SysChapter;
import com.example.reading.repository.EsBookRepository;
import com.example.reading.mapper.SysBookMapper;
import com.example.reading.mapper.SysChapterMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Elasticsearch 全文搜索服务
 * 负责数据同步和搜索查询
 */
@Service
public class BookSearchService {

    @Autowired
    private EsBookRepository esBookRepository;

    @Autowired
    private ElasticsearchOperations elasticsearchOperations;

    @Autowired
    private SysBookMapper sysBookMapper;

    @Autowired
    private SysChapterMapper chapterMapper;

    /**
     * 全量同步：将 MySQL 中的所有书籍及其章节内容同步到 Elasticsearch
     */
    public int syncAllBooksToEs() {
        // 1. 查出所有书籍
        List<SysBook> allBooks = sysBookMapper.selectList(null);
        List<EsBookDoc> docs = new ArrayList<>();

        for (SysBook book : allBooks) {
            EsBookDoc doc = buildEsDoc(book);
            docs.add(doc);
        }

        // 2. 批量写入 ES
        esBookRepository.saveAll(docs);
        return docs.size();
    }

    /**
     * 单本同步：同步指定书籍到 ES（新增/编辑/解析章节后调用）
     */
    public void syncOneBookToEs(Long bookId) {
        SysBook book = sysBookMapper.selectById(bookId);
        if (book == null) return;

        EsBookDoc doc = buildEsDoc(book);
        esBookRepository.save(doc);
    }

    /**
     * 从 ES 删除指定书籍文档
     */
    public void deleteFromEs(Long bookId) {
        esBookRepository.deleteById(bookId);
    }

    /**
     * 全文搜索
     * @param keyword  搜索关键词
     * @param category 分类（可选）
     * @param pageNum  页码（从1开始）
     * @param pageSize 每页大小
     * @return 搜索结果 Map（包含 records 和 total）
     */
    public Map<String, Object> search(String keyword, String category, int pageNum, int pageSize) {
        // 构建多字段加权搜索查询
        // title^3: 书名权重最高
        // author^2, description^2: 作者和简介次之
        // chapterContents: 正文权重最低但能搜到
        BoolQuery.Builder boolBuilder = new BoolQuery.Builder();

        // 主搜索：multi_match 跨字段
        boolBuilder.must(q -> q.multiMatch(MultiMatchQuery.of(mm -> mm
                .query(keyword)
                .fields("title^3", "author^2", "description^2", "chapterContents")
                .type(TextQueryType.BestFields)
                .minimumShouldMatch("1")
        )));

        // 分类过滤（如果有）
        if (category != null && !category.isEmpty() && !"全部".equals(category)) {
            boolBuilder.filter(f -> f.term(t -> t.field("category").value(category)));
        }

        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.bool(boolBuilder.build()))
                .withPageable(PageRequest.of(pageNum - 1, pageSize))
                .build();

        SearchHits<EsBookDoc> hits = elasticsearchOperations.search(query, EsBookDoc.class);

        // 组装返回结果
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
            record.put("score", hit.getScore()); // 搜索相关度评分
            records.add(record);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("total", hits.getTotalHits());
        return result;
    }

    /**
     * 内部方法：将 SysBook + 其章节内容组装为 ES 文档
     */
    private EsBookDoc buildEsDoc(SysBook book) {
        EsBookDoc doc = new EsBookDoc();
        doc.setId(book.getId());
        doc.setTitle(book.getTitle());
        doc.setAuthor(book.getAuthor());
        doc.setDescription(book.getDescription());
        doc.setCategory(book.getCategory());
        doc.setCoverUrl(book.getCoverUrl());

        // 查询该书的所有章节内容，拼接为一个大字符串
        QueryWrapper<SysChapter> cq = new QueryWrapper<>();
        cq.eq("book_id", book.getId());
        cq.orderByAsc("sort");
        List<SysChapter> chapters = chapterMapper.selectList(cq);

        String allContent = chapters.stream()
                .map(SysChapter::getContent)
                .filter(Objects::nonNull)
                .collect(Collectors.joining("\n"));

        // ES 单字段默认最大索引约 32KB 的 token，对于超长内容截取前 50000 字符
        // 这足以覆盖大多数书籍的核心内容用于搜索
        if (allContent.length() > 50000) {
            allContent = allContent.substring(0, 50000);
        }

        doc.setChapterContents(allContent);
        return doc;
    }
}
