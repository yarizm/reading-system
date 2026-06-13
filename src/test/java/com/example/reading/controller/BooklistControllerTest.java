package com.example.reading.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.reading.common.Result;
import com.example.reading.entity.Booklist;
import com.example.reading.entity.BooklistBook;
import com.example.reading.entity.SysBook;
import com.example.reading.mapper.BooklistBookMapper;
import com.example.reading.mapper.BooklistMapper;
import com.example.reading.mapper.UserBookshelfMapper;
import com.example.reading.service.AuthContextService;
import com.example.reading.service.ISysBookService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BooklistControllerTest {

    @Mock
    private BooklistMapper booklistMapper;

    @Mock
    private BooklistBookMapper booklistBookMapper;

    @Mock
    private UserBookshelfMapper shelfMapper;

    @Mock
    private ISysBookService sysBookService;

    @Mock
    private AuthContextService authContextService;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private BooklistController controller;

    @Test
    void createRejectsMissingBodyWithoutInsert() {
        when(authContextService.currentUserId(request)).thenReturn(7L);

        Result<?> result = controller.create(null, request);

        assertThat(result.getCode()).isEqualTo("400");
        verify(booklistMapper, never()).insert(any(Booklist.class));
    }

    @Test
    void createNormalizesTextAndKeepsGeneratedFields() {
        Booklist booklist = new Booklist();
        booklist.setName("  Weekend reads  ");
        booklist.setDescription("  calm notes  ");

        when(authContextService.currentUserId(request)).thenReturn(7L);

        Result<?> result = controller.create(booklist, request);

        assertThat(result.getCode()).isEqualTo("200");
        ArgumentCaptor<Booklist> captor = ArgumentCaptor.forClass(Booklist.class);
        verify(booklistMapper).insert(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(7L);
        assertThat(captor.getValue().getName()).isEqualTo("Weekend reads");
        assertThat(captor.getValue().getDescription()).isEqualTo("calm notes");
        assertThat(captor.getValue().getShareCode()).hasSize(8);
        assertThat(captor.getValue().getCreateTime()).isNotNull();
    }

    @Test
    void addBookRejectsMissingBodyWithoutLoadingList() {
        Result<?> result = controller.addBook(null, request);

        assertThat(result.getCode()).isEqualTo("400");
        verify(booklistMapper, never()).selectById(any());
        verify(booklistBookMapper, never()).insert(any(BooklistBook.class));
    }

    @Test
    void addBookRejectsMissingBookIdWithoutLoadingList() {
        BooklistBook link = new BooklistBook();
        link.setBooklistId(2L);

        Result<?> result = controller.addBook(link, request);

        assertThat(result.getCode()).isEqualTo("400");
        verify(booklistMapper, never()).selectById(any());
        verify(booklistBookMapper, never()).insert(any(BooklistBook.class));
    }

    @Test
    void addBookRejectsInaccessibleBook() {
        BooklistBook link = new BooklistBook();
        link.setBooklistId(2L);
        link.setBookId(9L);

        Booklist booklist = new Booklist();
        booklist.setId(2L);
        booklist.setUserId(7L);

        SysBook book = new SysBook();
        book.setId(9L);

        when(booklistMapper.selectById(2L)).thenReturn(booklist);
        when(authContextService.isSelf(7L, request)).thenReturn(true);
        when(authContextService.currentUserId(request)).thenReturn(7L);
        when(sysBookService.getById(9L)).thenReturn(book);
        when(authContextService.canAccessBook(book, 7L)).thenReturn(false);

        Result<?> result = controller.addBook(link, request);

        assertThat(result.getCode()).isEqualTo("403");
        verify(booklistBookMapper, never()).insert(any(BooklistBook.class));
    }

    @Test
    void addBookInsertsWhenTargetIsValidAndAccessible() {
        BooklistBook link = new BooklistBook();
        link.setBooklistId(2L);
        link.setBookId(9L);

        Booklist booklist = new Booklist();
        booklist.setId(2L);
        booklist.setUserId(7L);

        SysBook book = new SysBook();
        book.setId(9L);

        when(booklistMapper.selectById(2L)).thenReturn(booklist);
        when(authContextService.isSelf(7L, request)).thenReturn(true);
        when(authContextService.currentUserId(request)).thenReturn(7L);
        when(sysBookService.getById(9L)).thenReturn(book);
        when(authContextService.canAccessBook(book, 7L)).thenReturn(true);
        when(booklistBookMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);

        Result<?> result = controller.addBook(link, request);

        assertThat(result.getCode()).isEqualTo("200");
        verify(booklistBookMapper).insert(link);
    }
}
