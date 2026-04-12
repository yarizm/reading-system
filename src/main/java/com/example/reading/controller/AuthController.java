package com.example.reading.controller;

import cn.hutool.core.util.StrUtil;
import com.example.reading.common.Result;
import com.example.reading.entity.SysUser;
import com.example.reading.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 认证控制器
 * 处理验证码发送、验证码登录、注册及密码重置，所有 Redis 操作和业务逻辑委托给 AuthService。
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    /** 发送验证码（模拟：打印到控制台） */
    @PostMapping("/sendCode")
    public Result<?> sendCode(@RequestBody Map<String, Object> params) {
        String target = (String) params.get("target");
        Integer type = (Integer) params.get("type");

        if (StrUtil.isBlank(target) || type == null) {
            return Result.error("400", "参数错误");
        }

        String code = authService.generateAndCacheCode(target, type);

        System.out.println("=========================================");
        System.out.println("发送验证码给 " + target + " : " + code);
        System.out.println("=========================================");

        return Result.success("验证码已发送（请查看控制台）");
    }

    /** 验证码注册 */
    @PostMapping("/register")
    public Result<?> register(@RequestBody Map<String, Object> params) {
        try {
            authService.register(
                    (String) params.get("target"),
                    (String) params.get("code"),
                    (String) params.get("password"),
                    (String) params.get("nickname"),
                    (Integer) params.get("age")
            );
            return Result.success("注册成功");
        } catch (Exception e) {
            return Result.error("400", e.getMessage());
        }
    }

    /** 验证码登录 */
    @PostMapping("/loginByCode")
    public Result<?> loginByCode(@RequestBody Map<String, Object> params) {
        try {
            SysUser user = authService.loginByCode(
                    (String) params.get("target"),
                    (String) params.get("code")
            );
            return Result.success(user);
        } catch (Exception e) {
            return Result.error("400", e.getMessage());
        }
    }

    /** 重置密码 */
    @PostMapping("/resetPassword")
    public Result<?> resetPassword(@RequestBody Map<String, Object> params) {
        try {
            authService.resetPassword(
                    (String) params.get("target"),
                    (String) params.get("code"),
                    (String) params.get("newPassword")
            );
            return Result.success("密码重置成功");
        } catch (Exception e) {
            return Result.error("400", e.getMessage());
        }
    }
}
