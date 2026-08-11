package org.template.enums;

public enum RedisKey {


    // ---- 临时/安全（1.1 现有，保持口径一致）----
    CAPTCHA      ("note_backbond:captcha:%s"),            // 图形验证码，参数 uuid
    EMAIL_CODE   ("note_backbond:email:code:%s"),         // 邮箱验证码，参数 email
    LOGIN_FAIL   ("note_backbond:login:fail:%s"),         // 登录失败计数，参数 account
    LOGIN_LOCK   ("note_backbond:login:lock:%s"),         // 账号锁定，参数 account
    LOGOUT_TOKEN ("note_backbond:logout:token"),       // Token 注销黑名单

    // ---- 业务缓存（3.x 各节）----
    NOTE_BLOCKS  ("note_backbond:note:blocks:%s"),        // 笔记块 Hash，参数 noteId（3.1）
    NOTE_LIST    ("note_backbond:note:list:user:%s:page:%s:size:%s"), // 笔记列表分页，参数 userId,page,size（3.2）
    NOTEBOOK_LIST("note_backbond:notebook:list:user:%s"), // 笔记本列表，参数 userId（3.3）
    USER_INFO    ("note_backbond:user:info:%s"),          // 用户信息，参数 userId（3.4）
    TAG_LIST     ("note_backbond:tag:user:%s"),           // 标签列表，参数 userId（3.5）

    // ---- 锁 / 限流 ----
    PAGE_LOCK    ("note_backbond:lock:page:%s:%s"),       // 列表页互斥锁，参数 notebookId,userId（3.2）
    RATE_LIMIT   ("note_backbond:rate:limit:%s"),         // 接口限流，参数 key（3.7）
    ;
    private final String pattern;

    RedisKey(String pattern){
        this.pattern = pattern;
    }

    public String build(Object...args){
        return String.format(this.pattern,args);
    }

}
