package com.example.reading.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.reading.dto.UserDto;
import com.example.reading.entity.SysUser;
import com.example.reading.mapper.SysUserMapper;
import com.example.reading.service.ISysUserService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements ISysUserService {

    @Override
    public void register(UserDto userDto) {
        // 1. 校验用户名是否已存在
        QueryWrapper<SysUser> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", userDto.getUsername());
        if (count(queryWrapper) > 0) {
            throw new RuntimeException("用户名已存在，请更换！");
        }

        // 2. 创建新用户
        SysUser user = new SysUser();
        user.setUsername(userDto.getUsername());
        // 密码加密存储 (使用 MD5，防止数据库泄露后密码直接暴露)
        user.setPassword(userDto.getPassword());
        user.setNickname(userDto.getNickname());
        user.setAge(userDto.getAge());

        // 3. 保存到数据库
        save(user);
    }

    @Override
    public SysUser login(UserDto userDto) {
        // 1. 根据用户名查询
        QueryWrapper<SysUser> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", userDto.getUsername());
        SysUser user = getOne(queryWrapper);

        // 2. 校验用户是否存在
        if (user == null) {
            throw new RuntimeException("用户不存在！");
        }

        // 3. 校验密码 (将前端传来的密码加密后，和数据库比对)
        if (!user.getPassword().equals(userDto.getPassword())) {
            throw new RuntimeException("密码错误！");
        }

        // 4. 返回用户信息 (把密码置空，防止泄露给前端)
        user.setPassword(null);
        return user;
    }
}