package org.template.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * 邮件发送服务
 */
@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    /** 发件人地址（从配置中读取） */
    @Autowired
    private org.springframework.core.env.Environment env;

    /**
     * 发送登录验证码
     *
     * @param to   收件人邮箱
     * @param code 6 位验证码
     */
    public void sendVerificationCode(String to, String code) {
        String from = env.getProperty("spring.mail.username");

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject("Note 笔记 - 登录验证码");
        message.setText("您的登录验证码为：" + code + "，有效期5分钟，请勿泄露给他人。");

        mailSender.send(message);
    }
}