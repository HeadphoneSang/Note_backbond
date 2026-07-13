package org.template.model.dto;

/**
 * 发送邮箱验证码请求 DTO
 */
public class SendEmailCodeRequest {

    private String email;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}