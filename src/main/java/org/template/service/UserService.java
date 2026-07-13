package org.template.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.template.mapper.UserMapper;
import org.template.model.User;
import org.template.model.dto.LoginRequest;
import org.template.model.dto.RegisterRequest;
import org.template.model.dto.UpdateUserRequest;
import org.template.util.JwtUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * 用户业务层 — 注册 / 登录 / 信息管理
 */
@Service
public class UserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JwtUtil jwtUtil;

    /**
     * BCrypt 密码编码器（自动从 IoC 容器获取，由 PasswordEncoderConfig 提供）
     */
    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private EmailService emailService;

    // ================ 登录安全配置 ================

    /** 连续失败最大次数（超过后锁定账号） */
    private static final int MAX_LOGIN_ATTEMPTS = 5;

    /** 账号锁定时间（分钟） */
    private static final int LOCK_DURATION_MINUTES = 15;

    /** 失败计数 Redis Key 前缀 */
    private static final String LOGIN_FAIL_PREFIX = "login:fail:account:";

    /** 锁定状态 Redis Key 前缀 */
    private static final String LOGIN_LOCK_PREFIX = "login:lock:account:";

    /** 验证码 Redis Key 前缀 */
    private static final String CAPTCHA_PREFIX = "captcha:";

    /** 邮箱验证码 Redis Key 前缀 */
    private static final String EMAIL_CODE_PREFIX = "email:code:";

    /** 邮箱验证码有效期（分钟） */
    private static final int EMAIL_CODE_TTL_MINUTES = 5;

    /** 邮箱验证码重发间隔（秒） — 同一邮箱 60 秒内不允许重复发送 */
    private static final int EMAIL_CODE_RESEND_INTERVAL_SECONDS = 60;

    // ==================== 注册 ====================

    /**
     * 用户注册
     *
     * @param req 注册请求（account, password, nickname, phoneNumber）
     * @return 注册成功的用户信息（不含密码）
     */
    @Transactional
    public User register(RegisterRequest req) {
        // 1. 参数校验
        String account = req.getAccount();
        String password = req.getPassword();

        if (account == null || account.trim().isEmpty()) {
            throw new IllegalArgumentException("账号不能为空");
        }
        if (password == null || password.length() < 6) {
            throw new IllegalArgumentException("密码至少需要6位");
        }
        if (req.getCaptchaKey() == null || req.getCaptchaKey().trim().isEmpty()
                || req.getCaptchaCode() == null || req.getCaptchaCode().trim().isEmpty()) {
            throw new IllegalArgumentException("验证码不能为空");
        }

        String redisCaptchaKey =CAPTCHA_PREFIX + req.getCaptchaKey();
        String realCaptchaCode = stringRedisTemplate.opsForValue().get(redisCaptchaKey);
        stringRedisTemplate.delete(redisCaptchaKey);

        if (realCaptchaCode == null) {
            throw new IllegalArgumentException("验证码已过期，请刷新");
        }
        if (!realCaptchaCode.equals(req.getCaptchaCode().trim().toLowerCase())) {
            throw new IllegalArgumentException("验证码错误");
        }

        // 2. 检查账号是否已存在
        if (userMapper.selectCount(Wrappers.<User>lambdaQuery().eq(User::getAccount, account)) > 0) {
            throw new IllegalArgumentException("账号已被注册");
        }

        // 3. 创建新用户，密码用 BCrypt 加密存储
        User user = new User();
        user.setAccount(account);
        user.setPassword(passwordEncoder.encode(password));
        user.setNickname(req.getNickname() != null ? req.getNickname() : account);
        user.setPhoneNumber(req.getPhoneNumber());
        user.setEmail(req.getEmail());

        // 4. 保存到数据库
        userMapper.insert(user);
        return user;
    }

    // ==================== 登录 ====================

    /**
     * 用户登录（含验证码校验 + 暴力破解防护）
     *
     * @param req 登录请求（account, password, captchaKey, captchaCode）
     * @return 包含 token 和用户信息的 Map
     */
    public Map<String, Object> login(LoginRequest req) {
        // 1. 参数校验
        if (req.getAccount() == null || req.getAccount().trim().isEmpty()) {
            throw new IllegalArgumentException("账号不能为空");
        }
        if (req.getPassword() == null || req.getPassword().isEmpty()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        if (req.getCaptchaKey() == null || req.getCaptchaKey().trim().isEmpty()
                || req.getCaptchaCode() == null || req.getCaptchaCode().trim().isEmpty()) {
            throw new IllegalArgumentException("验证码不能为空");
        }

        String account = req.getAccount().trim();
        String lockKey = LOGIN_LOCK_PREFIX + account;
        String failKey = LOGIN_FAIL_PREFIX + account;

        // 2. 校验验证码（优先于账号锁定检查，避免泄露账号状态）
        String redisKey = CAPTCHA_PREFIX + req.getCaptchaKey();
        String storedCode = stringRedisTemplate.opsForValue().get(redisKey);
        // 无论校验成功还是失败，验证码都立即失效（一次性使用）
        stringRedisTemplate.delete(redisKey);
        if (storedCode == null) {
            throw new IllegalArgumentException("验证码已过期，请刷新");
        }
        if (!storedCode.equals(req.getCaptchaCode().trim().toLowerCase())) {
            throw new IllegalArgumentException("验证码错误");
        }

        // 3. 检查账号是否被锁定
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(lockKey))) {
            long ttl = stringRedisTemplate.getExpire(lockKey, TimeUnit.MINUTES);
            throw new IllegalArgumentException("账号已被临时锁定，请 " + ttl + " 分钟后再试");
        }

        // 4. 查找用户
        User user = Optional.ofNullable(userMapper.selectOne(
                Wrappers.<User>lambdaQuery().eq(User::getAccount, account)))
                .orElseThrow(() -> {
                    // 账号不存在，同样记录一次失败（防止枚举有效账号）
                    recordLoginFailure(failKey, lockKey, account);
                    return new IllegalArgumentException("账号或密码错误");
                });

        // 5. 验证密码
        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            recordLoginFailure(failKey, lockKey, account);
            throw new IllegalArgumentException("账号或密码错误");
        }

        // 6. 登录成功 → 清除失败计数
        stringRedisTemplate.delete(failKey);

        // 7. 生成 JWT Token
        String token = jwtUtil.generateToken(user.getId(), user.getAccount());

        // 8. 组装返回数据
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        user.setPassword(null);
        data.put("userInfo", user);
        return data;
    }

    /**
     * 记录一次登录失败，达到阈值时锁定账号
     */
    private void recordLoginFailure(String failKey, String lockKey, String account) {
        Long attempts = stringRedisTemplate.opsForValue().increment(failKey);
        // 第一次失败时设置 TTL，防止遗忘的计数永远占用内存
        if (attempts != null && attempts == 1) {
            // 失败计数的 TTL 略大于锁定时间 × 最大次数，确保锁定期内计数不会提前消失
            stringRedisTemplate.expire(failKey, LOCK_DURATION_MINUTES * 2, TimeUnit.MINUTES);
        }
        if (attempts != null && attempts >= MAX_LOGIN_ATTEMPTS) {
            // 达到上限 → 锁定账号
            stringRedisTemplate.opsForValue().set(lockKey, "1", LOCK_DURATION_MINUTES, TimeUnit.MINUTES);
            // 清除失败计数（解锁后重新计数）
            stringRedisTemplate.delete(failKey);
            // 此处可补充审计日志
            // log.warn("账号已被锁定 - account: {}", account);
        }
    }

    // ==================== 获取用户信息 ====================

    /**
     * 根据用户 ID 获取用户信息
     *
     * @param userId 用户 ID
     * @return 用户实体（密码字段会被置空，避免泄露）
     */
    public User getUserInfo(Integer userId) {
        User user = Optional.ofNullable(userMapper.selectById(userId))
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        // 密码不返回
        user.setPassword(null);
        return user;
    }

    // ==================== 更新用户信息 ====================

    /**
     * 更新用户基本信息（昵称、手机号、头像）
     */
    @Transactional
    public User updateUserInfo(Integer userId, UpdateUserRequest req) {
        User user = Optional.ofNullable(userMapper.selectById(userId))
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));

        if (req.getNickname() != null) {
            user.setNickname(req.getNickname());
        }
        if (req.getPhoneNumber() != null) {
            user.setPhoneNumber(req.getPhoneNumber());
        }
        if (req.getEmail() != null) {
            user.setEmail(req.getEmail());
        }
        if (req.getAvatar() != null) {
            user.setAvatar(req.getAvatar());
        }

        userMapper.updateById(user);
        user.setPassword(null);
        return user;
    }

    // ==================== 修改密码 ====================

    /**
     * 修改密码
     *
     * @param userId   用户 ID
     * @param oldPwd   旧密码
     * @param newPwd   新密码
     */
    @Transactional
    public void changePassword(Integer userId, String oldPwd, String newPwd) {
        // 1. 校验参数
        if (oldPwd == null || oldPwd.isEmpty()) {
            throw new IllegalArgumentException("旧密码不能为空");
        }
        if (newPwd == null || newPwd.length() < 6) {
            throw new IllegalArgumentException("新密码至少需要6位");
        }

        // 2. 查找用户
        User user = Optional.ofNullable(userMapper.selectById(userId))
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));

        // 3. 验证旧密码
        if (!passwordEncoder.matches(oldPwd, user.getPassword())) {
            throw new IllegalArgumentException("旧密码错误");
        }

        // 4. 更新密码
        user.setPassword(passwordEncoder.encode(newPwd));
        userMapper.updateById(user);
    }

    // ==================== 邮箱验证码发送 ====================

    /**
     * 向指定邮箱发送登录验证码
     *
     * @param email 目标邮箱
     */
    public void sendEmailCode(String email) {
        // 1. 参数校验
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("邮箱不能为空");
        }

        // 2. 检查是否在重发冷却期内
        String codeKey = EMAIL_CODE_PREFIX + email;
        String ttlKey = codeKey + ":ttl";
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(ttlKey))) {
            long remaining = stringRedisTemplate.getExpire(ttlKey, TimeUnit.SECONDS);
            throw new IllegalArgumentException("请 " + remaining + " 秒后再重新发送");
        }

        // 3. 生成 6 位随机验证码
        String code = String.format("%06d", new Random().nextInt(1000000));

        // 4. 存入 Redis，TTL 5 分钟
        stringRedisTemplate.opsForValue().set(codeKey, code, EMAIL_CODE_TTL_MINUTES, TimeUnit.MINUTES);
        // 冷却标记（60 秒）
        stringRedisTemplate.opsForValue().set(ttlKey, "1", EMAIL_CODE_RESEND_INTERVAL_SECONDS, TimeUnit.SECONDS);

        // 5. 发送邮件
        emailService.sendVerificationCode(email, code);
    }

    // ==================== 邮箱验证码登录 ====================

    /**
     * 邮箱验证码登录（未注册邮箱自动创建账号）
     *
     * @param email 邮箱
     * @param code  验证码
     * @return token 和用户信息
     */
    @Transactional
    public Map<String, Object> emailLogin(String email, String code) {
        // 1. 参数校验
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("邮箱不能为空");
        }
        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException("验证码不能为空");
        }

        // 2. 从 Redis 获取验证码
        String codeKey = EMAIL_CODE_PREFIX + email;
        String realCode = stringRedisTemplate.opsForValue().get(codeKey);
        // 一次性使用，立即删除
        stringRedisTemplate.delete(codeKey);
        stringRedisTemplate.delete(codeKey + ":ttl");

        // 3. 校验验证码
        if (realCode == null) {
            throw new IllegalArgumentException("验证码已过期，请重新获取");
        }
        if (!realCode.equals(code.trim())) {
            throw new IllegalArgumentException("验证码错误");
        }

        // 4. 查找或创建用户
        email = email.trim();
        User user = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getEmail, email));
        if (user == null) {
            user = new User();
            // 从邮箱 @ 前部分生成 account
            String account = email.substring(0, email.indexOf('@'));
            // 检查 account 是否已存在，若存在则追加随机后缀
            if (userMapper.selectCount(Wrappers.<User>lambdaQuery().eq(User::getAccount, account)) > 0) {
                String suffix = String.format("%04x", new Random().nextInt(0x10000));
                account = account + "_" + suffix;
            }
            user.setAccount(account);
            user.setNickname(account);
            user.setEmail(email);
            // 邮箱登录用户无密码，标记为不可用密码
            user.setPassword("");
            userMapper.insert(user);
        }

        // 5. 生成 JWT Token
        String token = jwtUtil.generateToken(user.getId(), user.getEmail());

        // 6. 组装返回数据
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        user.setPassword(null);
        data.put("userInfo", user);
        return data;
    }
}
