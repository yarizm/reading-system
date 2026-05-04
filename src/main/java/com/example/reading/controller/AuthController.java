package com.example.reading.controller;

import cn.hutool.core.util.StrUtil;
import com.example.reading.common.Result;
import com.example.reading.entity.SysUser;
import com.example.reading.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    private AuthService authService;

    @PostMapping("/sendCode")
    public Result<?> sendCode(@RequestBody Map<String, Object> params) {
        String target = (String) params.get("target");
        Integer type = (Integer) params.get("type");

        if (StrUtil.isBlank(target) || type == null) {
            return Result.error("400", "参数错误");
        }

        String code = authService.generateAndCacheCode(target, type);
        log.debug("Verification code generated. target={}, type={}, code={}",
                maskTarget(target), type, code);

        return Result.success("验证码已发送（开发环境可在 DEBUG 日志中查看）");
    }

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

    private String maskTarget(String target) {
        if (StrUtil.isBlank(target) || target.length() <= 4) {
            return "****";
        }
        return target.substring(0, 2) + "****" + target.substring(target.length() - 2);
    }
}
