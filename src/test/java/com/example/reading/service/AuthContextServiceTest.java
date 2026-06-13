package com.example.reading.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.reading.entity.SysBook;
import com.example.reading.entity.SysUser;
import com.example.reading.mapper.BookShareMapper;
import com.example.reading.mapper.FriendshipMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthContextServiceTest {

    @Mock
    private ISysUserService sysUserService;

    @Mock
    private AuthTokenService authTokenService;

    @Mock
    private FriendshipMapper friendshipMapper;

    @Mock
    private BookShareMapper bookShareMapper;

    @InjectMocks
    private AuthContextService authContextService;

    @Test
    void currentUserIdCachesActiveUserWithinRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        SysUser user = new SysUser();
        user.setId(42L);

        when(authTokenService.resolveUserId(request)).thenReturn(42L);
        when(sysUserService.getById(42L)).thenReturn(user);

        assertThat(authContextService.currentUserId(request)).isEqualTo(42L);
        assertThat(authContextService.currentUserId(request)).isEqualTo(42L);

        verify(authTokenService, times(1)).resolveUserId(request);
        verify(sysUserService, times(1)).getById(42L);
    }

    @Test
    void currentUserIdCachesMissingActiveUserWithinRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        when(authTokenService.resolveUserId(request)).thenReturn(null);

        assertThat(authContextService.currentUserId(request)).isNull();
        assertThat(authContextService.currentUserId(request)).isNull();

        verify(authTokenService, times(1)).resolveUserId(request);
        verify(sysUserService, times(0)).getById(any());
    }

    @Test
    void isAdminOnlyAllowsRoleOneUsers() {
        SysUser normalUser = new SysUser();
        normalUser.setId(10L);
        normalUser.setRole(0);

        SysUser adminUser = new SysUser();
        adminUser.setId(11L);
        adminUser.setRole(1);

        when(sysUserService.getById(10L)).thenReturn(normalUser);
        when(sysUserService.getById(11L)).thenReturn(adminUser);

        assertThat(authContextService.isAdmin((Long) null)).isFalse();
        assertThat(authContextService.isAdmin(10L)).isFalse();
        assertThat(authContextService.isAdmin(11L)).isTrue();
    }

    @Test
    void canViewBookAllowsPublicUploaderAdminAndSharedReceiver() {
        SysBook privateBook = new SysBook();
        privateBook.setId(20L);
        privateBook.setUploaderId(100L);
        privateBook.setStatus(1);

        SysBook publicBook = new SysBook();
        publicBook.setId(21L);
        publicBook.setStatus(2);

        SysUser normalUser = new SysUser();
        normalUser.setId(101L);
        normalUser.setRole(0);

        SysUser adminUser = new SysUser();
        adminUser.setId(102L);
        adminUser.setRole(1);

        when(sysUserService.getById(101L)).thenReturn(normalUser);
        when(sysUserService.getById(102L)).thenReturn(adminUser);
        when(bookShareMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);

        assertThat(authContextService.canViewBook(publicBook, (Long) null)).isTrue();
        assertThat(authContextService.canViewBook(privateBook, 100L)).isTrue();
        assertThat(authContextService.canViewBook(privateBook, 102L)).isTrue();
        assertThat(authContextService.canViewBook(privateBook, 101L)).isFalse();

        when(bookShareMapper.selectCount(any(QueryWrapper.class))).thenReturn(1L);

        assertThat(authContextService.canViewBook(privateBook, 101L)).isTrue();
    }

    @Test
    void canViewBookSkipsShareLookupForPublicBooks() {
        SysBook publicBook = new SysBook();
        publicBook.setId(40L);
        publicBook.setStatus(2);

        assertThat(authContextService.canViewBook(publicBook, 101L)).isTrue();

        verify(bookShareMapper, times(0)).selectCount(any(QueryWrapper.class));
    }

    @Test
    void canAccessBookDoesNotTreatSharesAsFullBookAccess() {
        SysBook privateBook = new SysBook();
        privateBook.setId(30L);
        privateBook.setUploaderId(100L);
        privateBook.setStatus(1);

        SysUser normalUser = new SysUser();
        normalUser.setId(101L);
        normalUser.setRole(0);

        when(sysUserService.getById(101L)).thenReturn(normalUser);

        assertThat(authContextService.canAccessBook(null, 101L)).isFalse();
        assertThat(authContextService.canAccessBook(privateBook, null)).isFalse();
        assertThat(authContextService.canAccessBook(privateBook, 100L)).isTrue();
        assertThat(authContextService.canAccessBook(privateBook, 101L)).isFalse();
    }
}
