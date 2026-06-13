package com.example.reading.service;

import com.example.reading.entity.*;
import com.example.reading.utils.PaginationUtils;
import com.example.reading.repository.EsNoteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class NoteEsService {

    private static final Logger log = LoggerFactory.getLogger(NoteEsService.class);

    @Autowired(required = false)
    private EsNoteRepository esNoteRepository;

    @Autowired(required = false)
    private ElasticsearchOperations elasticsearchOperations;

    @Autowired
    private ISysBookService sysBookService;

    @Autowired
    private INoteTagService noteTagService;

    @Autowired
    private ISysTagService tagService;

    @Autowired
    private NoteTagViewService noteTagViewService;

    @Value("${app.elasticsearch.enabled:false}")
    private boolean esEnabled;

    @Async
    public void syncNoteToEs(SysNote note) {
        if (!esEnabled || esNoteRepository == null) return;
        try {
            EsNoteDoc doc = buildEsDoc(note);
            esNoteRepository.save(doc);
        } catch (Exception e) {
            log.warn("ES 笔记操作失败: {}", e.getMessage());
        }
    }

    @Async
    public void deleteNoteFromEs(Long noteId) {
        if (!esEnabled || esNoteRepository == null) return;
        try {
            esNoteRepository.deleteById(noteId);
        } catch (Exception e) {
            log.warn("ES 笔记操作失败: {}", e.getMessage());
        }
    }

    @Async
    public void syncNoteTagsToEs(Long noteId) {
        if (!esEnabled || esNoteRepository == null) return;
        try {
            esNoteRepository.findById(noteId).ifPresent(doc -> {
                List<Long> tagIds = noteTagService.getTagIdsByNoteId(noteId);
                doc.setTagIds(tagIds.stream().map(String::valueOf).toList());
                if (!tagIds.isEmpty()) {
                    List<SysTag> tags = tagService.listByIds(tagIds);
                    doc.setTagNames(tags.stream().map(SysTag::getName).collect(Collectors.joining(" ")));
                } else {
                    doc.setTagNames("");
                }
                esNoteRepository.save(doc);
            });
        } catch (Exception e) {
            log.warn("ES 笔记操作失败: {}", e.getMessage());
        }
    }

    public Map<String, Object> searchNotes(Long userId, String keyword, Long tagId, Long bookId, int page, int size) {
        if (!esEnabled || elasticsearchOperations == null) return null;

        try {
            int safePage = PaginationUtils.pageNum(page);
            int safeSize = PaginationUtils.pageSize(size);
            Criteria criteria = Criteria.where("userId").is(userId);

            if (keyword != null && !keyword.trim().isEmpty()) {
                Criteria keywordCriteria = new Criteria("selectedText").matches(keyword.trim())
                        .or(new Criteria("content").matches(keyword.trim()))
                        .or(new Criteria("bookTitle").matches(keyword.trim()));
                criteria = criteria.and(keywordCriteria);
            }
            if (bookId != null) {
                criteria = criteria.and(new Criteria("bookId").is(bookId));
            }
            if (tagId != null) {
                criteria = criteria.and(new Criteria("tagIds").is(String.valueOf(tagId)));
            }

            CriteriaQuery query = new CriteriaQuery(criteria);
            query.setPageable(PageRequest.of(safePage - 1, safeSize));

            SearchHits<EsNoteDoc> hits = elasticsearchOperations.search(query, EsNoteDoc.class);

            // 批量获取标签（2 次查询代替 N*2 次）
            List<Long> noteIds = hits.stream().map(h -> h.getContent().getId()).toList();
            Map<Long, List<Map<String, Object>>> tagInfoByNoteId = noteTagViewService.listTagInfoByNoteIds(noteIds);

            List<Map<String, Object>> records = new ArrayList<>();
            for (SearchHit<EsNoteDoc> hit : hits) {
                EsNoteDoc doc = hit.getContent();
                Map<String, Object> item = new HashMap<>();
                item.put("id", doc.getId());
                item.put("bookId", doc.getBookId());
                item.put("selectedText", doc.getSelectedText());
                item.put("content", doc.getContent());
                item.put("createTime", doc.getCreateTime());
                item.put("bookTitle", doc.getBookTitle());

                item.put("tags", tagInfoByNoteId.getOrDefault(doc.getId(), List.of()));
                records.add(item);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("records", records);
            result.put("total", hits.getTotalHits());
            return result;
        } catch (Exception e) {
            log.warn("ES 笔记搜索失败，回退到 MySQL: {}", e.getMessage());
            return null;
        }
    }

    private EsNoteDoc buildEsDoc(SysNote note) {
        EsNoteDoc doc = new EsNoteDoc();
        doc.setId(note.getId());
        doc.setUserId(note.getUserId());
        doc.setBookId(note.getBookId());
        doc.setSelectedText(note.getSelectedText());
        doc.setContent(note.getContent());
        doc.setCreateTime(note.getCreateTime());

        SysBook book = sysBookService.getById(note.getBookId());
        if (book != null) doc.setBookTitle(book.getTitle());

        List<Long> tagIds = noteTagService.getTagIdsByNoteId(note.getId());
        doc.setTagIds(tagIds.stream().map(String::valueOf).toList());
        if (!tagIds.isEmpty()) {
            List<SysTag> tags = tagService.listByIds(tagIds);
            doc.setTagNames(tags.stream().map(SysTag::getName).collect(Collectors.joining(" ")));
        }
        return doc;
    }
}
