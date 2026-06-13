package com.example.reading.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.reading.entity.SysBook;
import com.example.reading.entity.SysNote;
import com.example.reading.entity.UserBookshelf;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InsightServiceTest {

    @Mock
    private IUserBookshelfService userBookshelfService;

    @Mock
    private ISysNoteService sysNoteService;

    @Mock
    private ISysBookService sysBookService;

    @InjectMocks
    private InsightService insightService;

    @Test
    void collectUserReadingStatsBatchLoadsRecentReadingBooks() {
        UserBookshelf first = new UserBookshelf();
        first.setBookId(11L);
        first.setIsFinished(0);
        first.setCurrentChapterIndex(1);
        first.setLastReadTime(LocalDateTime.now());

        UserBookshelf second = new UserBookshelf();
        second.setBookId(12L);
        second.setIsFinished(0);
        second.setCurrentChapterIndex(0);
        second.setLastReadTime(LocalDateTime.now().minusDays(1));

        SysBook firstBook = new SysBook();
        firstBook.setId(11L);
        firstBook.setTitle("First Book");

        SysBook secondBook = new SysBook();
        secondBook.setId(12L);
        secondBook.setTitle("Second Book");

        SysNote note = new SysNote();
        note.setContent("recent note");

        when(userBookshelfService.list(any(QueryWrapper.class))).thenReturn(List.of(first, second));
        when(sysBookService.listByIds(any())).thenReturn(List.of(firstBook, secondBook));
        when(sysNoteService.list(any(QueryWrapper.class))).thenReturn(List.of(note));

        Map<String, Object> stats = insightService.collectUserReadingStats(7L);

        assertThat(stats).containsEntry("total_books", 2);
        assertThat(stats.get("recent_reading_books").toString())
                .contains("First Book(进度:第2章)")
                .contains("Second Book(进度:第1章)");
        verify(sysBookService, never()).getById(anyLong());
    }
}
