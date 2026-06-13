package com.example.reading.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class BookIndexSyncService {

    private static final Logger log = LoggerFactory.getLogger(BookIndexSyncService.class);

    private final BookSearchService bookSearchService;
    private final DifyKnowledgeBaseService difyKnowledgeBaseService;

    public BookIndexSyncService(ObjectProvider<BookSearchService> bookSearchServiceProvider,
                                DifyKnowledgeBaseService difyKnowledgeBaseService) {
        this.bookSearchService = bookSearchServiceProvider.getIfAvailable();
        this.difyKnowledgeBaseService = difyKnowledgeBaseService;
    }

    public void syncBook(Long bookId) {
        syncToEs(bookId);
        syncToKb(bookId);
    }

    public void deleteBook(Long bookId) {
        deleteFromEs(bookId);
        deleteFromKb(bookId);
    }

    private void syncToEs(Long bookId) {
        if (bookSearchService == null) {
            return;
        }
        try {
            bookSearchService.syncOneBookToEs(bookId);
        } catch (Exception e) {
            log.error("Failed to sync book to ES. bookId={}", bookId, e);
        }
    }

    private void deleteFromEs(Long bookId) {
        if (bookSearchService == null) {
            return;
        }
        try {
            bookSearchService.deleteFromEs(bookId);
        } catch (Exception e) {
            log.error("Failed to delete book from ES. bookId={}", bookId, e);
        }
    }

    private void syncToKb(Long bookId) {
        try {
            difyKnowledgeBaseService.syncOneBookToKb(bookId);
        } catch (Exception e) {
            log.error("Failed to sync book to KB. bookId={}", bookId, e);
        }
    }

    private void deleteFromKb(Long bookId) {
        try {
            difyKnowledgeBaseService.deleteFromKb(bookId);
        } catch (Exception e) {
            log.error("Failed to delete book from KB. bookId={}", bookId, e);
        }
    }
}
