package com.example.reading.controller;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.reading.common.Result;
import com.example.reading.dto.UserDto;
import com.example.reading.entity.SysUser;
import com.example.reading.mapper.UserBookshelfMapper;
import com.example.reading.service.ISysUserService;
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

    /** 用户名 + 密码登录 */
    @PostMapping("/login")
    public Result<SysUser> login(@RequestBody UserDto userDto) {
        try {
            SysUser user = sysUserService.login(userDto);
            return Result.success(user);
        } catch (Exception e) {
            return Result.error("500", e.getMessage());
        }
    }

    /** 更新用户基本信息（昵称、头像等，不含密码和用户名） */
    @PostMapping("/update")
    public Result<?> update(@RequestBody SysUser user) {
        user.setPassword(null);
        user.setUsername(null);
        sysUserService.updateById(user);
        SysUser currentUser = sysUserService.getById(user.getId());
        return Result.success(currentUser);
    }

    /** 修改密码 */
    @PostMapping("/password")
    public Result<?> updatePassword(@RequestBody SysUser user) {
        if (StrUtil.isBlank(user.getPassword())) {
            return Result.error("500", "新密码不能为空");
        }
        user.setPassword(BCrypt.hashpw(user.getPassword(), BCrypt.gensalt()));
        sysUserService.updateById(user);
        return Result.success();
    }

    /** 用户列表（管理员，分页 + 搜索） */
    @GetMapping("/list")
    public Result<?> getUserList(@RequestParam(defaultValue = "1") Integer pageNum,
                                 @RequestParam(defaultValue = "10") Integer pageSize,
                                 @RequestParam(defaultValue = "") String username) {
        Page<SysUser> page = new Page<>(pageNum, pageSize);
        QueryWrapper<SysUser> query = new QueryWrapper<>();
        if (!username.isEmpty()) {
            query.like("username", username).or().like("nickname", username);
        }
        query.orderByDesc("id");
        return Result.success(sysUserService.page(page, query));
    }

    /** 查看用户公开资料（含书架，管理员可越权查看私密资料） */
    @GetMapping("/profile/{id}")
    public Result<?> getUserProfile(@PathVariable Long id, @RequestParam(required = false) Long viewerId) {
        SysUser user = sysUserService.getById(id);
        if (user == null) {
            return Result.error("404", "用户不存在");
        }

        boolean isAdmin = false;
        if (viewerId != null) {
            SysUser viewer = sysUserService.getById(viewerId);
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
            List<Map<String, Object>> shelf = shelfMapper.selectMyShelf(id);
            profile.put("shelfList", shelf);
        }

        return Result.success(profile);
    }

    /** 删除用户（管理员，超级管理员 ID=1 不可删除） */
    @DeleteMapping("/{id}")
    public Result<?> deleteUser(@PathVariable Long id) {
        if (id == 1L) {
            return Result.error("500", "无法删除超级管理员");
        }
        sysUserService.removeById(id);
        return Result.success();
    }

    /** 封禁/解封用户（管理员） */
    @PostMapping("/ban/{id}")
    public Result<?> banUser(@PathVariable Long id, @RequestParam Boolean banned) {
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