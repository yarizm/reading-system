package com.example.reading.controller;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.reading.common.IpUtil;
import com.example.reading.common.Result;
import com.example.reading.dto.UserDto;
import com.example.reading.entity.SysUser;
import com.example.reading.mapper.UserBookshelfMapper;
import com.example.reading.service.AuthContextService;
import com.example.reading.service.AuthTokenService;
import com.example.reading.service.ISysUserService;
import com.example.reading.service.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户管理控制器
 * 提供用户登录、资料更新、密码修改、用户列表查询（管理员）、用户删除（管理员）及公开资料查看功能。
 */
@RestController
@RequestMapping("/sysUser")
public class SysUserController {

    @Autowired
    private ISysUserService sysUserService;

    @Autowired
    private UserBookshelfMapper shelfMapper;

    @Autowired
    private AuthTokenService authTokenService;

    @Autowired
    private AuthContextService authContextService;

    @Autowired
    private RateLimitService rateLimitService;

    private Long currentUserId(HttpServletRequest request) {
        return authContextService.currentUserId(request);
    }

    private boolean isAdmin(HttpServletRequest request) {
        return authContextService.isAdmin(request);
    }

    private boolean isSelf(Long userId, HttpServletRequest request) {
        Long currentUserId = currentUserId(request);
        return currentUserId != null && currentUserId.equals(userId);
    }

    private SysUser sanitizeUser(SysUser user) {
        if (user != null) {
            user.setPassword(null);
            user.setToken(null);
        }
        return user;
    }

    /** 用户名 + 密码登录 */
    @PostMapping("/login")
    public Result<SysUser> login(@RequestBody UserDto userDto, HttpServletRequest request) {
        String clientIp = IpUtil.getClientIp(request);
        if (!rateLimitService.isAllowed("login:" + clientIp, 10, 60)) {
            return Result.error("429", "登录尝试过于频繁，请60秒后再试");
        }
        try {
            SysUser user = sysUserService.login(userDto);
            user.setToken(authTokenService.createToken(user.getId()));
            return Result.success(user);
        } catch (Exception e) {
            return Result.error("500", e.getMessage());
        }
    }

    /** 更新用户基本信息（昵称、头像等，不含密码和用户名） */
    @PostMapping("/update")
    public Result<?> update(@RequestBody SysUser user, HttpServletRequest request) {
        Long currentUserId = currentUserId(request);
        if (currentUserId == null) {
            return Result.error("403", "Forbidden");
        }
        SysUser existing = sysUserService.getById(currentUserId);
        if (existing == null) {
            return Result.error("404", "User not found");
        }
        if (user.getNickname() != null) existing.setNickname(user.getNickname());
        if (user.getAvatar() != null) existing.setAvatar(user.getAvatar());
        if (user.getAge() != null) existing.setAge(user.getAge());
        if (user.getPreferences() != null) existing.setPreferences(user.getPreferences());
        if (user.getHealthLimitTime() != null) existing.setHealthLimitTime(user.getHealthLimitTime());
        if (user.getPreferredVoice() != null) existing.setPreferredVoice(user.getPreferredVoice());
        if (user.getShelfVisible() != null) existing.setShelfVisible(user.getShelfVisible());
        if (user.getInfoVisible() != null) existing.setInfoVisible(user.getInfoVisible());
        if (user.getEmail() != null) existing.setEmail(user.getEmail());
        if (user.getPhone() != null) existing.setPhone(user.getPhone());
        sysUserService.updateById(existing);
        existing.setPassword(null);
        existing.setToken(authTokenService.createToken(existing.getId()));
        return Result.success(existing);
    }

    /** 管理员专用的用户信息修改接口（允许更高权限的字段更新） */
    @PostMapping("/adminUpdate")
    public Result<?> adminUpdate(@RequestBody SysUser user,
                                 @RequestParam(required = false) Long operatorId,
                                 HttpServletRequest request) {
        Long currentUserId = currentUserId(request);
        if (!isAdmin(request)) {
            return Result.error("403", "Forbidden");
        }
        if (user.getId() == null) {
            return Result.error("500", "用户ID不能为空");
        }
        
        SysUser originalUser = sysUserService.getById(user.getId());
        if (originalUser == null) {
            return Result.error("404", "被修改的用户不存在");
        }

        // 核心校验：拦截非法角色权限篡改
        if (user.getRole() != null && !user.getRole().equals(originalUser.getRole())) {
            // 只有顶级管理员 (id=1) 才能修改角色
            if (currentUserId == null || currentUserId != 1L) {
                return Result.error("403", "越权操作：只有最高管理员有权修改管理权限");
            }
            // 防止自己取消自己的管理员权限
            if (user.getId().equals(currentUserId)) {
                return Result.error("403", "安全受限：不可修改自己的管理员权限");
            }
        }

        // 管理员更新不修改原密码和用户名
        user.setPassword(null);
        user.setUsername(null);
        
        // 执行更新
        sysUserService.updateById(user);
        return Result.success(sanitizeUser(sysUserService.getById(user.getId())));
    }

    /** 修改密码（需提供旧密码） */
    @PostMapping("/password")
    public Result<?> updatePassword(@RequestBody SysUser user, HttpServletRequest request) {
        Long currentUserId = currentUserId(request);
        if (currentUserId == null) {
            return Result.error("403", "Forbidden");
        }
        if (StrUtil.isBlank(user.getOldPassword())) {
            return Result.error("500", "旧密码不能为空");
        }
        if (StrUtil.isBlank(user.getPassword())) {
            return Result.error("500", "新密码不能为空");
        }
        SysUser existingUser = sysUserService.getById(currentUserId);
        if (existingUser == null) {
            return Result.error("404", "用户不存在");
        }
        if (!BCrypt.checkpw(user.getOldPassword(), existingUser.getPassword())) {
            return Result.error("500", "旧密码不正确");
        }
        SysUser passwordUpdate = new SysUser();
        passwordUpdate.setId(currentUserId);
        passwordUpdate.setPassword(BCrypt.hashpw(user.getPassword(), BCrypt.gensalt()));
        sysUserService.updateById(passwordUpdate);
        return Result.success();
    }

    /** 用户列表（管理员，分页 + 搜索） */
    @GetMapping("/list")
    public Result<?> getUserList(@RequestParam(defaultValue = "1") Integer pageNum,
                                 @RequestParam(defaultValue = "10") Integer pageSize,
                                 @RequestParam(defaultValue = "") String username,
                                 HttpServletRequest request) {
        if (!isAdmin(request)) {
            return Result.error("403", "Forbidden");
        }
        Page<SysUser> page = new Page<>(pageNum, pageSize);
        QueryWrapper<SysUser> query = new QueryWrapper<>();
        if (!username.isEmpty()) {
            query.like("username", username).or().like("nickname", username);
        }
        query.orderByDesc("id");
        Page<SysUser> resultPage = sysUserService.page(page, query);
        resultPage.getRecords().forEach(this::sanitizeUser);
        return Result.success(resultPage);
    }

    /** 获取当前登录用户信息（用于前端验证 Token 有效性和角色） */
    @GetMapping("/me")
    public Result<?> getCurrentUser(HttpServletRequest request) {
        Long userId = currentUserId(request);
        if (userId == null) {
            return Result.error("401", "未登录");
        }
        SysUser user = sysUserService.getById(userId);
        if (user == null) {
            return Result.error("404", "用户不存在");
        }
        Map<String, Object> result = new HashMap<>();
        result.put("id", user.getId());
        result.put("username", user.getUsername());
        result.put("nickname", user.getNickname());
        result.put("avatar", user.getAvatar());
        result.put("role", user.getRole());
        result.put("age", user.getAge());
        result.put("preferences", user.getPreferences());
        result.put("email", user.getEmail());
        result.put("phone", user.getPhone());
        result.put("createTime", user.getCreateTime());
        result.put("shelfVisible", user.getShelfVisible());
        result.put("infoVisible", user.getInfoVisible());
        result.put("preferredVoice", user.getPreferredVoice());
        result.put("healthLimitTime", user.getHealthLimitTime());
        return Result.success(result);
    }

    /** 查看用户公开资料（含书架，管理员可越权查看私密资料） */
    @GetMapping("/profile/{id}")
    public Result<?> getUserProfile(@PathVariable Long id,
                                    @RequestParam(required = false) Long viewerId,
                                    HttpServletRequest request) {
        SysUser user = sysUserService.getById(id);
        if (user == null) {
            return Result.error("404", "用户不存在");
        }

        boolean isAdmin = false;
        Long currentUserId = currentUserId(request);
        if (currentUserId != null) {
            SysUser viewer = sysUserService.getById(currentUserId);
            if (viewer != null && viewer.getRole() != null && viewer.getRole() == 1) {
                isAdmin = true;
            }
        }

        boolean showInfo = (user.getInfoVisible() == null || user.getInfoVisible() == 1) || isAdmin;
        boolean showShelf = (user.getShelfVisible() == null || user.getShelfVisible() == 1) || isAdmin;

        Map<String, Object> profile = new HashMap<>();
        profile.put("id", user.getId());
        profile.put("nickname", user.getNickname());
        profile.put("avatar", user.getAvatar());
        profile.put("createTime", user.getCreateTime());
        profile.put("infoVisible", user.getInfoVisible() == null ? 1 : user.getInfoVisible());
        profile.put("shelfVisible", user.getShelfVisible() == null ? 1 : user.getShelfVisible());
        profile.put("viewedByAdmin", isAdmin);

        if (showInfo) {
            profile.put("age", user.getAge());
            profile.put("preferences", user.getPreferences());
        }
        if (showShelf) {
            List<Map<String, Object>> shelf = (isAdmin || isSelf(id, request))
                    ? shelfMapper.selectMyShelf(id)
                    : shelfMapper.selectPublicShelf(id);
            profile.put("shelfList", shelf);
        }

        return Result.success(profile);
    }

    /** 删除用户（管理员，超级管理员 ID=1 不可删除） */
    @DeleteMapping("/{id}")
    public Result<?> deleteUser(@PathVariable Long id, HttpServletRequest request) {
        if (!isAdmin(request)) {
            return Result.error("403", "Forbidden");
        }
        if (id == 1L) {
            return Result.error("500", "无法删除超级管理员");
        }
        sysUserService.removeById(id);
        return Result.success();
    }

    /** 封禁/解封用户（管理员） */
    @PostMapping("/ban/{id}")
    public Result<?> banUser(@PathVariable Long id,
                             @RequestParam Boolean banned,
                             HttpServletRequest request) {
        if (!isAdmin(request)) {
            return Result.error("403", "Forbidden");
        }
        if (id == 1L) {
            return Result.error("500", "无法封禁超级管理员");
        }
        SysUser user = sysUserService.getById(id);
        if (user == null) {
            return Result.error("404", "用户不存在");
        }
        user.setIsBanned(banned ? 1 : 0);
        sysUserService.updateById(user);
        return Result.success(banned ? "已封禁该用户" : "已解封该用户");
    }
}
