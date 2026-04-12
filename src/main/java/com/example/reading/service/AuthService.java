package com.example.reading.service;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.reading.entity.SysUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 认证服务
 * 封装验证码的生成/校验（Redis）以及注册、验证码登录、密码重置等业务逻辑。
 */
@Service
public class AuthService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ISysUserService sysUserService;

    private static final String CODE_KEY_PREFIX = "validation:code:";
    private static final long CODE_EXPIRE_MINUTES = 5;

    /**
     * 生成并缓存 6 位验证码
     * @param target 手机号或邮箱
     * @param type   1=注册, 2=登录, 3=找回密码
     * @return 生成的验证码（用于调试输出）
     */
    public String generateAndCacheCode(String target, Integer type) {
        String code = RandomUtil.randomNumbers(6);
        String key = CODE_KEY_PREFIX + type + ":" + target;
        redisTemplate.opsForValue().set(key, code, CODE_EXPIRE_MINUTES, TimeUnit.MINUTES);
        return code;
    }

    /**
     * 校验验证码
     * @throws RuntimeException 验证码无效或已过期时抛出
     */
    public void verifyCode(String target, String code, Integer type) {
        String key = CODE_KEY_PREFIX + type + ":" + target;
        String savedCode = redisTemplate.opsForValue().get(key);
        if (savedCode == null || !savedCode.equals(code)) {
            throw new RuntimeException("验证码无效或已过期");
        }
    }

    /** 通过验证码注册新用户 */
    public void register(String target, String code, String password, String nickname, Integer age) {
        if (StrUtil.hasBlank(target, code, password)) {
            throw new RuntimeException("请填写完整信息");
        }

        verifyCode(target, code, 1);

        if (sysUserService.getByUsername(target) != null) {
            throw new RuntimeException("该账号已注册");
        }

        SysUser user = new SysUser();
        user.setUsername(target);
        user.setPassword(BCrypt.hashpw(password, BCrypt.gensalt()));
        user.setNickname(nickname);
        user.setAge(age);
        user.setRole(0);
        user.setCreateTime(LocalDateTime.now());

        if (target.contains("@")) {
            user.setEmail(target);
        } else {
            user.setPhone(target);
        }

        sysUserService.save(user);
    }

    /** 通过验证码登录，返回用户对象 */
    public SysUser loginByCode(String target, String code) {
        verifyCode(target, code, 2);

        QueryWrapper<SysUser> query = new QueryWrapper<>();
        query.eq("username", target)
             .or().eq("phone", target)
             .or().eq("email", target);
        SysUser user = sysUserService.getOne(query);

        if (user == null) {
            throw new RuntimeException("用户不存在，请先注册");
        }
        if (user.getIsBanned() != null && user.getIsBanned() == 1) {
            throw new RuntimeException("该账号已被封禁");
        }
        return user;
    }

    /** 通过验证码重置密码 */
    public void resetPassword(String target, String code, String newPassword) {
        if (StrUtil.hasBlank(target, code, newPassword)) {
            throw new RuntimeException("参数不完整");
        }

        verifyCode(target, code, 3);

        QueryWrapper<SysUser> query = new QueryWrapper<>();
        query.eq("username", target)
             .or().eq("phone", target)
             .or().eq("email", target);
        SysUser user = sysUserService.getOne(query);

        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        user.setPassword(BCrypt.hashpw(newPassword, BCrypt.gensalt()));
        sysUserService.updateById(user);
    }
}
