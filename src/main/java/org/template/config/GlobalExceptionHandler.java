package org.template.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.template.exceptions.NoteBlockConflictException;
import org.template.exceptions.OptimisticLockException;
import org.template.model.dto.NoteDiffResult;
import org.template.model.vo.Result;

/**
 * 全局异常拦截器
 * 统一处理 Controller 层抛出的异常，返回标准 Result 格式
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 请求参数错误 */
    private static final int BAD_REQUEST = HttpStatus.BAD_REQUEST.value();          // 400
    /** 资源冲突（如乐观锁） */
    private static final int CONFLICT   = HttpStatus.CONFLICT.value();              // 409

    /** 缺少必要参数 */
    private static final String MSG_MISSING_PARAM = "缺少必要参数: ";
    /** 请求体格式错误 */
    private static final String MSG_BAD_BODY      = "请求体格式错误";

    /**
     * 乐观锁冲突（版本号不一致）
     */
    @ExceptionHandler(OptimisticLockException.class)
    public Result<Void> handleOptimisticLock(OptimisticLockException e) {
        return Result.error(CONFLICT, e.getMessage());
    }

    @ExceptionHandler(NoteBlockConflictException.class)
    public Result<NoteDiffResult> handleNoteBlockConflict(NoteBlockConflictException e) {
        return Result.error(CONFLICT, e.getMessage(), e.getResult());
    }

    /**
     * 请求参数缺失
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingParam(MissingServletRequestParameterException e) {
        return Result.error(BAD_REQUEST, MSG_MISSING_PARAM + e.getParameterName());
    }

    /**
     * 请求体格式错误（如 JSON 解析失败）
     */
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public Result<Void> handleMessageNotReadable() {
        return Result.error(BAD_REQUEST, MSG_BAD_BODY);
    }

    /**
     * 非法参数
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public Result<Void> handleIllegalArgument(IllegalArgumentException e) {
        return Result.error(BAD_REQUEST, e.getMessage());
    }

    /**
     * 兜底异常 — 未捕获的运行时异常
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("未预期的异常", e);
        return Result.serverError();
    }
}