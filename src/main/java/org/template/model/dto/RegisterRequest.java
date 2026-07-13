package org.template.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 注册请求 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {

    private String account;
    private String password;
    private String nickname;
    private String email;
    private String phoneNumber;
    /** 验证码唯一标识（由 /captcha 接口返回） */
    private String captchaKey;

    /** 用户输入的验证码 */
    private String captchaCode;


}
