package com.example.reading.service;

import com.example.reading.entity.*;
import com.example.reading.repository.EsNoteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

    @Autowired
    private ISysBookService sysBookService;

    @Autowired
    private INoteTagService noteTagService;

    @Autowired
    private ISysTagService tagService;

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
        // ES 笔记搜索尚未完整实现，返回 null 让 Controller 回退到 MySQL
        return null;
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
