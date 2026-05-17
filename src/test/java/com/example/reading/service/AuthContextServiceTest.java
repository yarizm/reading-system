package com.example.reading.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.reading.entity.SysBook;
import com.example.reading.entity.SysUser;
import com.example.reading.mapper.BookShareMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthContextServiceTest {

    @Mock
    private ISysUserService sysUserService;

    @Mock
    private BookShareMapper bookShareMapper;

    @InjectMocks
    private AuthContextService authContextService;

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
