package com.example.reading.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.reading.common.Result;
import com.example.reading.entity.SysBook;
import com.example.reading.entity.UserBookshelf;
import com.example.reading.mapper.UserBookshelfMapper;
import com.example.reading.service.AuthContextService;
import com.example.reading.service.ISysBookService;
import com.example.reading.service.ISysUserService;
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
class UserBookshelfControllerTest {

    @Mock
    private UserBookshelfMapper shelfMapper;

    @Mock
    private AuthContextService authContextService;

    @Mock
    private ISysUserService sysUserService;

    @Mock
    private ISysBookService sysBookService;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private UserBookshelfController controller;

    @Test
    void addRejectsMissingShelfTargetWithoutLookingUpBook() {
        UserBookshelf shelf = new UserBookshelf();
        shelf.setUserId(7L);

        Result<?> result = controller.add(shelf, request);

        assertThat(result.getCode()).isEqualTo("400");
        verify(sysBookService, never()).getById(any());
        verify(shelfMapper, never()).insert(any(UserBookshelf.class));
    }

    @Test
    void addInitializesReadingProgressDefaults() {
        UserBookshelf shelf = new UserBookshelf();
        shelf.setUserId(7L);
        shelf.setBookId(3L);

        SysBook book = new SysBook();
        book.setId(3L);
        book.setStatus(2);

        when(authContextService.currentUserId(request)).thenReturn(7L);
        when(sysBookService.getById(3L)).thenReturn(book);
        when(authContextService.canAccessBook(book, 7L)).thenReturn(true);
        when(shelfMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);

        Result<?> result = controller.add(shelf, request);

        assertThat(result.getCode()).isEqualTo("200");
        ArgumentCaptor<UserBookshelf> captor = ArgumentCaptor.forClass(UserBookshelf.class);
        verify(shelfMapper).insert(captor.capture());
        assertThat(captor.getValue().getProgressIndex()).isZero();
        assertThat(captor.getValue().getIsFinished()).isZero();
        assertThat(captor.getValue().getCurrentChapterIndex()).isZero();
        assertThat(captor.getValue().getLastReadTime()).isNotNull();
    }

    @Test
    void updateProgressRejectsMissingShelfTarget() {
        UserBookshelf shelf = new UserBookshelf();
        shelf.setUserId(7L);

        Result<?> result = controller.updateProgress(shelf, request);

        assertThat(result.getCode()).isEqualTo("400");
        verify(shelfMapper, never()).selectOne(any());
        verify(shelfMapper, never()).updateById(any(UserBookshelf.class));
    }
}
