package org.template.model.dto;

/**
 * 邮箱验证码登录请求 DTO
 */
public class EmailLoginRequest {

    private String email;
    private String code;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
}