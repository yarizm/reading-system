package com.example.reading.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.reading.entity.SysBook;
import com.example.reading.entity.SysChapter;
import com.example.reading.mapper.SysBookMapper;
import com.example.reading.mapper.SysChapterMapper;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Dify Knowledge Base 同步服务
 * 将书籍章节内容按章节粒度同步到 Dify 知识库，实现向量化检索。
 * 所有方法均为 best-effort：异常不向外传播。
 */
@Service
public class DifyKnowledgeBaseService {

    private static final Logger log = LoggerFactory.getLogger(DifyKnowledgeBaseService.class);

    @Autowired
    private SysBookMapper sysBookMapper;

    @Autowired
    private SysChapterMapper chapterMapper;

    @Value("${dify.kb.api-url}")
    private String kbApiUrl;

    @Value("${dify.kb.api-key}")
    private String kbApiKey;

    @Value("${dify.kb.dataset-id}")
    private String datasetId;

    @Value("${dify.kb.chapter-delay-ms:200}")
    private long chapterDelayMs;

    private final Gson gson = new Gson();

    /** 异步同步单本书到 KB（章节级别），遍历所有章节逐章创建/更新 Dify 文档 */
    @Async("kbSyncExecutor")
    public void syncOneBookToKb(Long bookId) {
        try {
            SysBook book = sysBookMapper.selectById(bookId);
            if (book == null) {
                log.warn("KB sync: book not found. bookId={}", bookId);
                return;
            }
            if (Integer.valueOf(4).equals(book.getStatus())) {
                deleteFromKb(bookId);
                return;
            }

            QueryWrapper<SysChapter> cq = new QueryWrapper<>();
            cq.eq("book_id", bookId);
            cq.orderByAsc("sort");
            List<SysChapter> chapters = chapterMapper.selectList(cq);

            if (chapters.isEmpty()) {
                log.info("KB sync: book has no chapters, skipping. bookId={}, title={}", bookId, book.getTitle());
                return;
            }

            log.info("KB sync: starting chapter-level sync. bookId={}, title={}, chapters={}",
                    bookId, book.getTitle(), chapters.size());

            boolean hasFailures = false;
            for (int i = 0; i < chapters.size(); i++) {
                SysChapter chapter = chapters.get(i);
                try {
                    if (chapter.getKbDocumentId() == null || chapter.getKbDocumentId().isEmpty()) {
                        createChapterDocument(book, chapter);
                    } else {
                        updateChapterDocument(book, chapter);
                    }
                } catch (Exception e) {
                    hasFailures = true;
                    log.error("KB sync failed for chapter. bookId={}, chapterId={}, sort={}, title=\"{}\"",
                            bookId, chapter.getId(), chapter.getSort(), chapter.getTitle(), e);
                }

                if (i < chapters.size() - 1 && chapterDelayMs > 0) {
                    try {
                        Thread.sleep(chapterDelayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }

            if (hasFailures) {
                log.warn("KB sync completed with failures. bookId={}, title={}", bookId, book.getTitle());
            } else {
                log.info("KB sync completed. bookId={}, title={}, chapters={}",
                        bookId, book.getTitle(), chapters.size());
            }
        } catch (Exception e) {
            log.error("KB sync failed. bookId={}", bookId, e);
        }
    }

    /** 异步从 KB 删除一本书的所有章节文档 */
    @Async("kbSyncExecutor")
    public void deleteFromKb(Long bookId) {
        try {
            SysBook book = sysBookMapper.selectById(bookId);
            if (book == null) {
                log.warn("KB delete: book not found. bookId={}", bookId);
                return;
            }

            QueryWrapper<SysChapter> cq = new QueryWrapper<>();
            cq.eq("book_id", bookId);
            List<SysChapter> chapters = chapterMapper.selectList(cq);

            int deleted = 0;
            for (SysChapter chapter : chapters) {
                if (chapter.getKbDocumentId() != null && !chapter.getKbDocumentId().isEmpty()) {
                    try {
                        deleteDocument(chapter.getKbDocumentId());
                        chapter.setKbDocumentId(null);
                        chapterMapper.updateById(chapter);
                        deleted++;
                    } catch (Exception e) {
                        log.error("Failed to delete KB document for chapter. chapterId={}, docId={}",
                                chapter.getId(), chapter.getKbDocumentId(), e);
                    }
                }
            }
            log.info("KB delete: removed {} chapter documents. bookId={}, title={}",
                    deleted, bookId, book.getTitle());
        } catch (Exception e) {
            log.error("KB delete failed. bookId={}", bookId, e);
        }
    }

    /** 全量同步所有已公开书籍（管理员手动触发） */
    @Async("kbSyncExecutor")
    public void syncAllBooksToKb() {
        QueryWrapper<SysBook> query = new QueryWrapper<>();
        query.eq("status", 2);
        List<SysBook> books = sysBookMapper.selectList(query);
        log.info("KB full sync: found {} published books", books.size());
        for (SysBook book : books) {
            syncOneBookToKb(book.getId());
        }
    }

    // ===================== 私有方法 =====================

    /** 为单个章节创建 Dify 文档 */
    private void createChapterDocument(SysBook book, SysChapter chapter) {
        String docName = buildChapterDocumentName(book, chapter);
        String content = buildChapterContent(book, chapter);

        JsonObject body = new JsonObject();
        body.addProperty("name", docName);
        body.addProperty("text", content);
        body.addProperty("indexing_technique", "high_quality");
        JsonObject processRule = new JsonObject();
        processRule.addProperty("mode", "automatic");
        body.add("process_rule", processRule);

        // 元数据：book_id 用于按书过滤检索，book_name 用于展示
        JsonObject metadata = new JsonObject();
        metadata.addProperty("book_id", String.valueOf(book.getId()));
        metadata.addProperty("book_name", book.getTitle());
        body.add("metadata", metadata);

        try {
            String resp = WebClient.create().post()
                    .uri(kbApiUrl + "/datasets/" + datasetId + "/document/create_by_text")
                    .header("Authorization", "Bearer " + kbApiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body.toString())
                    .retrieve()
                    .onStatus(status -> status.isError(),
                            response -> response.bodyToMono(String.class)
                                    .flatMap(errBody -> {
                                        log.error("Dify create_by_text error. status={}, body={}",
                                                response.statusCode(), errBody);
                                        return Mono.error(new RuntimeException("Dify error: " + errBody));
                                    }))
                    .bodyToMono(String.class)
                    .block();

            if (resp != null) {
                JsonObject json = gson.fromJson(resp, JsonObject.class);
                if (json.has("document") && json.getAsJsonObject("document").has("id")) {
                    String docId = json.getAsJsonObject("document").get("id").getAsString();
                    chapter.setKbDocumentId(docId);
                    chapterMapper.updateById(chapter);
                    log.info("KB chapter document created. chapterId={}, title=\"{}\", docId={}",
                            chapter.getId(), chapter.getTitle(), docId);
                } else {
                    log.warn("KB create response missing document.id. resp={}", resp);
                }
            }
        } catch (Exception e) {
            log.error("KB chapter document create failed. chapterId={}, title=\"{}\", contentLength={}",
                    chapter.getId(), chapter.getTitle(), content.length(), e);
            throw new RuntimeException(e);
        }
    }

    /** 更新单个章节的 Dify 文档 */
    private void updateChapterDocument(SysBook book, SysChapter chapter) {
        String docName = buildChapterDocumentName(book, chapter);
        String content = buildChapterContent(book, chapter);

        JsonObject body = new JsonObject();
        body.addProperty("name", docName);
        body.addProperty("text", content);
        body.addProperty("indexing_technique", "high_quality");
        JsonObject processRule = new JsonObject();
        processRule.addProperty("mode", "automatic");
        body.add("process_rule", processRule);

        JsonObject metadata = new JsonObject();
        metadata.addProperty("book_id", String.valueOf(book.getId()));
        metadata.addProperty("book_name", book.getTitle());
        body.add("metadata", metadata);

        try {
            WebClient.create().post()
                    .uri(kbApiUrl + "/datasets/" + datasetId + "/documents/"
                            + chapter.getKbDocumentId() + "/update_by_text")
                    .header("Authorization", "Bearer " + kbApiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body.toString())
                    .retrieve()
                    .onStatus(status -> status.isError(),
                            response -> response.bodyToMono(String.class)
                                    .flatMap(errBody -> {
                                        log.error("Dify update_by_text error. status={}, body={}",
                                                response.statusCode(), errBody);
                                        return Mono.error(new RuntimeException("Dify error: " + errBody));
                                    }))
                    .bodyToMono(String.class)
                    .block();
            log.info("KB chapter document updated. chapterId={}, title=\"{}\", docId={}",
                    chapter.getId(), chapter.getTitle(), chapter.getKbDocumentId());
        } catch (Exception e) {
            log.error("KB chapter document update failed. chapterId={}, title=\"{}\", docId={}, contentLength={}",
                    chapter.getId(), chapter.getTitle(), chapter.getKbDocumentId(), content.length(), e);
            throw new RuntimeException(e);
        }
    }

    /** DELETE /v1/datasets/{id}/documents/{docId} */
    private void deleteDocument(String documentId) {
        try {
            WebClient.create().delete()
                    .uri(kbApiUrl + "/datasets/" + datasetId + "/documents/" + documentId)
                    .header("Authorization", "Bearer " + kbApiKey)
                    .retrieve()
                    .onStatus(status -> status.value() == 404,
                            response -> {
                                log.warn("Dify document not found (404), treating as already deleted. docId={}", documentId);
                                return Mono.empty();
                            })
                    .onStatus(status -> status.isError() && status.value() != 404,
                            response -> response.bodyToMono(String.class)
                                    .flatMap(errBody -> {
                                        log.error("Dify delete error. status={}, body={}",
                                                response.statusCode(), errBody);
                                        return Mono.error(new RuntimeException("Dify error: " + errBody));
                                    }))
                    .toBodilessEntity()
                    .block();
            log.info("KB document deleted. docId={}", documentId);
        } catch (Exception e) {
            log.error("KB document delete failed. docId={}", documentId, e);
            throw new RuntimeException(e);
        }
    }

    /** 构建章节文档名称: "{书名} - Ch{sort+1}: {章节标题}" */
    private String buildChapterDocumentName(SysBook book, SysChapter chapter) {
        int displayNum = chapter.getSort() != null ? chapter.getSort() + 1 : 1;
        return book.getTitle() + " - Ch" + displayNum + ": " + chapter.getTitle();
    }

    /** 构建章节文档内容（Markdown，含书籍上下文） */
    private String buildChapterContent(SysBook book, SysChapter chapter) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(book.getTitle()).append("\n\n");
        if (book.getAuthor() != null && !book.getAuthor().isEmpty()) {
            sb.append("> 作者：").append(book.getAuthor()).append("\n\n");
        }
        if (chapter.getTitle() != null && !chapter.getTitle().isEmpty()) {
            sb.append("## ").append(chapter.getTitle()).append("\n\n");
        }
        if (chapter.getContent() != null && !chapter.getContent().isEmpty()) {
            sb.append(chapter.getContent()).append("\n\n");
        }
        return sb.toString();
    }
}
