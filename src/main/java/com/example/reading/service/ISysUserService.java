package com.example.reading.service;

import com.example.reading.dto.UserDto;
import com.example.reading.entity.SysUser;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 用户表 服务类
 * </p>
 *
 * @author CodingAssistant
 * @since 2026-01-31
 */
public interface ISysUserService extends IService<SysUser> {
    // 注册
    void register(UserDto userDto);
    // 登录
    SysUser login(UserDto userDto);

    SysUser getByUsername(String username);
}
