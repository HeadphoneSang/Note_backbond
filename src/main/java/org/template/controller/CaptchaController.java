package org.template.controller;

import com.wf.captcha.SpecCaptcha;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.template.model.vo.Result;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 验证码控制器 — 生成图形验证码
 */
@RestController
public class CaptchaController {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /** 验证码 Redis Key 前缀 */
    private static final String CAPTCHA_PREFIX = "captcha:";

    /** 验证码过期时间（分钟） */
    private static final int CAPTCHA_EXPIRE_MINUTES = 5;

    /**
     * 获取图形验证码
     * GET /captcha
     * <p>
     * 返回格式：
     * {
     *   "key": "uuid-xxxx",          // 验证码唯一标识，登录时需携带
     *   "image": "data:image/png;base64,..."  // base64 编码的图片
     * }
     */
    @GetMapping("/captcha")
    public Result<Map<String, String>> getCaptcha() {
        // 生成 4 位数字验证码，图片宽 130px、高 48px
        SpecCaptcha captcha = new SpecCaptcha(130, 48, 4);
        String code = captcha.text().toLowerCase();   // 验证码转为小写再存储
        String key = UUID.randomUUID().toString();

        // 存入 Redis，5 分钟过期
        stringRedisTemplate.opsForValue().set(
                CAPTCHA_PREFIX + key,
                code,
                CAPTCHA_EXPIRE_MINUTES,
                TimeUnit.MINUTES
        );

        // 返回 key 和 base64 图片
        Map<String, String> data = new HashMap<>();
        data.put("key", key);
        data.put("image", captcha.toBase64());
        return Result.success(data);
    }
}