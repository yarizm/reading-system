package com.example.reading.controller;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.reading.common.Result;
import com.example.reading.entity.SysUser;
import com.example.reading.entity.SysValidation;
import com.example.reading.mapper.SysValidationMapper;
import com.example.reading.service.ISysUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private SysValidationMapper validationMapper;

    @Autowired
    private ISysUserService sysUserService;

    /**
     * 发送验证码
     * @param params { target: "手机/邮箱", type: 1注册/2登录/3找回密码 }
     */
    @PostMapping("/sendCode")
    public Result<?> sendCode(@RequestBody Map<String, Object> params) {
        String target = (String) params.get("target");
        Integer type = (Integer) params.get("type");

        if (StrUtil.isBlank(target) || type == null) {
            return Result.error("400", "参数错误");
        }

        // 生成6位验证码
        String code = RandomUtil.randomNumbers(6);

        // 保存到数据库
        SysValidation validation = new SysValidation();
        validation.setTarget(target);
        validation.setCode(code);
        validation.setType(type);
        validation.setExpireTime(LocalDateTime.now().plusMinutes(5));
        validationMapper.insert(validation);

        // 模拟发送：打印到控制台
        System.out.println("=========================================");
        System.out.println("发送验证码给 " + target + " : " + code);
        System.out.println("=========================================");

        return Result.success("验证码已发送（请查看控制台）");
    }

    /**
     * 邮箱/手机号注册
     */
    @PostMapping("/register")
    public Result<?> register(@RequestBody Map<String, Object> params) {
        String target = (String) params.get("target");
        String code = (String) params.get("code");
        String password = (String) params.get("password");
        String nickname = (String) params.get("nickname");
        Integer age = (Integer) params.get("age");

        if (StrUtil.hasBlank(target, code, password)) {
            return Result.error("400", "请填写完整信息");
        }

        // 校验验证码
        verifyCode(target, code, 1);

        // 检查用户是否已存在 (username, phone, email 均不可重复)
        // 这里简化：username 默认为 target
        if (sysUserService.getByUsername(target) != null) {
            return Result.error("400", "该账号已注册");
        }

        SysUser user = new SysUser();
        user.setUsername(target); // 默认用户名 = 手机/邮箱
        user.setPassword(password); // 实际应加密
        user.setNickname(nickname);
        user.setAge(age);
        user.setRole(0); // 普通用户
        user.setCreateTime(LocalDateTime.now());
        
        // 判断是手机还是邮箱
        if (target.contains("@")) {
            user.setEmail(target);
        } else {
            user.setPhone(target);
        }

        sysUserService.save(user);
        return Result.success("注册成功");
    }

    /**
     * 验证码登录
     */
    @PostMapping("/loginByCode")
    public Result<?> loginByCode(@RequestBody Map<String, Object> params) {
        String target = (String) params.get("target");
        String code = (String) params.get("code");

        verifyCode(target, code, 2);

        // 查询用户
        QueryWrapper<SysUser> query = new QueryWrapper<>();
        query.eq("username", target)
             .or().eq("phone", target)
             .or().eq("email", target);
        SysUser user = sysUserService.getOne(query);

        if (user == null) {
            return Result.error("404", "用户不存在，请先注册");
        }

        return Result.success(user);
    }

    /**
     * 重置密码
     */
    @PostMapping("/resetPassword")
    public Result<?> resetPassword(@RequestBody Map<String, Object> params) {
        String target = (String) params.get("target");
        String code = (String) params.get("code");
        String newPassword = (String) params.get("newPassword");

         if (StrUtil.hasBlank(target, code, newPassword)) {
            return Result.error("400", "参数不完整");
        }

        verifyCode(target, code, 3);

        QueryWrapper<SysUser> query = new QueryWrapper<>();
        query.eq("username", target)
             .or().eq("phone", target)
             .or().eq("email", target);
        SysUser user = sysUserService.getOne(query);

        if (user == null) {
            return Result.error("404", "用户不存在");
        }

        user.setPassword(newPassword);
        sysUserService.updateById(user);

        return Result.success("密码重置成功");
    }

    /**
     * 校验验证码工具方法
     */
    private void verifyCode(String target, String code, Integer type) {
        QueryWrapper<SysValidation> query = new QueryWrapper<>();
        query.eq("target", target)
             .eq("type", type)
             .eq("code", code)
             .gt("expire_time", LocalDateTime.now())
             .orderByDesc("id")
             .last("limit 1");
        
        SysValidation validation = validationMapper.selectOne(query);
        if (validation == null) {
            throw new RuntimeException("验证码无效或已过期");
        }
    }
}
