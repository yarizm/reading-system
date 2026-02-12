package com.example.reading.controller;

import cn.hutool.core.util.StrUtil;
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

@RestController
@RequestMapping("/sysUser")
public class SysUserController {

    @Autowired
    private ISysUserService sysUserService;

    @Autowired
    private UserBookshelfMapper shelfMapper;

    // 登录接口
    @PostMapping("/login")
    public Result<SysUser> login(@RequestBody UserDto userDto) {
        try {
            SysUser user = sysUserService.login(userDto);
            return Result.success(user);
        } catch (Exception e) {
            return Result.error("500", e.getMessage());
        }
    }

    // 注册接口
    @PostMapping("/register")
    public Result<?> register(@RequestBody UserDto userDto) {
        try {
            sysUserService.register(userDto);
            return Result.success();
        } catch (Exception e) {
            return Result.error("500", e.getMessage());
        }
    }
    /**
     * 更新用户信息 (用于修改昵称、头像)
     */
    @PostMapping("/update")
    public Result<?> update(@RequestBody SysUser user) {
        // 安全起见，不允许通过此接口修改密码，密码走单独接口
        user.setPassword(null);
        user.setUsername(null); // 用户名通常也不允许改

        sysUserService.updateById(user);

        // 更新后，返回最新的用户信息给前端，方便前端刷新缓存
        SysUser currentUser = sysUserService.getById(user.getId());
        return Result.success(currentUser);
    }

    /**
     * 修改密码
     */
    @PostMapping("/password")
    public Result<?> updatePassword(@RequestBody SysUser user) {
        // 前端传过来 id 和 password (新密码)
        if (StrUtil.isBlank(user.getPassword())) {
            return Result.error("500", "新密码不能为空");
        }
        // 实际项目中这里应该校验旧密码，这里简化直接重置
        sysUserService.updateById(user);
        return Result.success();
    }
    /**
     * [管理员] 获取用户列表 (分页 + 搜索)
     */
    @GetMapping("/list")
    public Result<?> getUserList(@RequestParam(defaultValue = "1") Integer pageNum,
                                 @RequestParam(defaultValue = "10") Integer pageSize,
                                 @RequestParam(defaultValue = "") String username) {
        Page<SysUser> page = new Page<>(pageNum, pageSize);
        QueryWrapper<SysUser> query = new QueryWrapper<>();

        if (!username.isEmpty()) {
            query.like("username", username).or().like("nickname", username);
        }

        query.orderByDesc("id"); // 最新注册的在前面
        return Result.success(sysUserService.page(page, query));
    }

    /**
     * 查看用户公开资料 (含公开书架)
     */
    @GetMapping("/profile/{id}")
    public Result<?> getUserProfile(@PathVariable Long id) {
        SysUser user = sysUserService.getById(id);
        if (user == null) {
            return Result.error("404", "用户不存在");
        }

        Map<String, Object> profile = new HashMap<>();
        profile.put("id", user.getId());
        profile.put("nickname", user.getNickname());
        profile.put("avatar", user.getAvatar());
        profile.put("preferences", user.getPreferences());
        profile.put("createTime", user.getCreateTime());
        profile.put("shelfVisible", user.getShelfVisible() != null ? user.getShelfVisible() : 1);

        // 若书架公开，查出书架列表
        Integer visible = user.getShelfVisible();
        if (visible == null || visible == 1) {
            List<Map<String, Object>> shelf = shelfMapper.selectMyShelf(id);
            profile.put("shelfList", shelf);
        }

        return Result.success(profile);
    }

    /**
     * [管理员] 删除用户
     */
    @DeleteMapping("/{id}")
    public Result<?> deleteUser(@PathVariable Long id) {
        // 保护机制：防止删除 ID 为 1 的超级管理员
        if (id == 1L) {
            return Result.error("500", "无法删除超级管理员");
        }
        sysUserService.removeById(id);
        return Result.success();
    }
}