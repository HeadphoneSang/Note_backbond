package org.template.model.dto;

/**
 * 登录请求 DTO
 */
public class LoginRequest {

    private String account;
    private String password;

    /** 验证码唯一标识（由 /captcha 接口返回） */
    private String captchaKey;

    /** 用户输入的验证码 */
    private String captchaCode;

    public String getAccount() { return account; }
    public void setAccount(String account) { this.account = account; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getCaptchaKey() { return captchaKey; }
    public void setCaptchaKey(String captchaKey) { this.captchaKey = captchaKey; }

    public String getCaptchaCode() { return captchaCode; }
    public void setCaptchaCode(String captchaCode) { this.captchaCode = captchaCode; }
}