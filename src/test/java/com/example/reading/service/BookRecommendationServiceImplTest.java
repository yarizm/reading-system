package com.example.reading.service;

import com.example.reading.entity.SysBook;
import com.example.reading.mapper.SysBookMapper;
import com.example.reading.mapper.UserBookshelfMapper;
import com.example.reading.service.impl.BookRecommendationServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.guoshiqiufeng.dify.chat.DifyChat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookRecommendationServiceImplTest {

    @Mock
    private UserBookshelfMapper userBookshelfMapper;

    @Mock
    private SysBookMapper sysBookMapper;

    @Mock
    private DifyChat difyChat;

    @Test
    void randomFallbackUsesCountAndWindowQuery() {
        SysBook book = new SysBook();
        book.setId(20L);
        book.setTitle("Fallback Book");

        when(sysBookMapper.countPublicBooks()).thenReturn(12L);
        when(sysBookMapper.selectPublicBooksWindow(anyInt(), anyLong())).thenReturn(List.of(book));

        BookRecommendationServiceImpl service = new BookRecommendationServiceImpl(
                userBookshelfMapper, sysBookMapper, new ObjectMapper(), null, difyChat);

        List<SysBook> result = service.recommendHomeBooks(null, false);

        assertThat(result).containsExactly(book);
        ArgumentCaptor<Integer> limitCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Long> offsetCaptor = ArgumentCaptor.forClass(Long.class);
        verify(sysBookMapper).selectPublicBooksWindow(limitCaptor.capture(), offsetCaptor.capture());
        assertThat(limitCaptor.getValue()).isEqualTo(8);
        assertThat(offsetCaptor.getValue()).isBetween(0L, 4L);
    }

    @Test
    void randomFallbackReturnsEmptyListWhenNoPublicBooksExist() {
        when(sysBookMapper.countPublicBooks()).thenReturn(0L);

        BookRecommendationServiceImpl service = new BookRecommendationServiceImpl(
                userBookshelfMapper, sysBookMapper, new ObjectMapper(), null, difyChat);

        List<SysBook> result = service.recommendHomeBooks(null, false);

        assertThat(result).isEmpty();
        verify(sysBookMapper, never()).selectPublicBooksWindow(anyInt(), anyLong());
    }
}
