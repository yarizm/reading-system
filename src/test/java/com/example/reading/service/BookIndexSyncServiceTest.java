package com.example.reading.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookIndexSyncServiceTest {

    @Test
    void syncBookInvokesSearchAndKnowledgeBaseSync() {
        ObjectProvider<BookSearchService> provider = mock(ObjectProvider.class);
        BookSearchService bookSearchService = mock(BookSearchService.class);
        DifyKnowledgeBaseService knowledgeBaseService = mock(DifyKnowledgeBaseService.class);
        when(provider.getIfAvailable()).thenReturn(bookSearchService);

        BookIndexSyncService service = new BookIndexSyncService(provider, knowledgeBaseService);

        service.syncBook(7L);

        verify(bookSearchService).syncOneBookToEs(7L);
        verify(knowledgeBaseService).syncOneBookToKb(7L);
    }

    @Test
    void syncBookKeepsKnowledgeBaseSyncWhenSearchSyncFails() {
        ObjectProvider<BookSearchService> provider = mock(ObjectProvider.class);
        BookSearchService bookSearchService = mock(BookSearchService.class);
        DifyKnowledgeBaseService knowledgeBaseService = mock(DifyKnowledgeBaseService.class);
        when(provider.getIfAvailable()).thenReturn(bookSearchService);
        doThrow(new RuntimeException("boom")).when(bookSearchService).syncOneBookToEs(8L);

        BookIndexSyncService service = new BookIndexSyncService(provider, knowledgeBaseService);

        service.syncBook(8L);

        verify(knowledgeBaseService).syncOneBookToKb(8L);
    }

    @Test
    void deleteBookSkipsSearchDeleteWhenSearchServiceIsDisabled() {
        ObjectProvider<BookSearchService> provider = mock(ObjectProvider.class);
        BookSearchService bookSearchService = mock(BookSearchService.class);
        DifyKnowledgeBaseService knowledgeBaseService = mock(DifyKnowledgeBaseService.class);
        when(provider.getIfAvailable()).thenReturn(null);

        BookIndexSyncService service = new BookIndexSyncService(provider, knowledgeBaseService);

        service.deleteBook(9L);

        verify(bookSearchService, never()).deleteFromEs(9L);
        verify(knowledgeBaseService).deleteFromKb(9L);
    }
}
