package org.template.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.template.model.Notebook;
import org.template.model.dto.AddNotebookRequest;
import org.template.model.vo.PageResult;
import org.template.model.vo.Result;
import org.template.service.NotebookService;

import javax.servlet.http.HttpServletRequest;

/**
 * 笔记本控制器 — 笔记本的增删改查
 */
@RestController
@RequestMapping("/notebook")
public class NotebookController {

    @Autowired
    private NotebookService notebookService;

    /**
     * 创建笔记本
     * POST /notebook/add
     */
    @PostMapping("/add")
    public Result<Notebook> addNotebook(@RequestBody AddNotebookRequest req) {
        // 参数校验
        if (req.getName() == null || req.getName().trim().isEmpty()) {
            return Result.error("笔记本名称不能为空");
        }
        if (req.getUserId() == null) {
            return Result.error("用户ID不能为空");
        }

        Notebook notebook = notebookService.addNotebook(
                req.getName().trim(),
                req.getDescription(),
                req.getUserId()
        );
        return Result.success("创建笔记本成功", notebook);
    }

    /**
     * 分页查询当前用户的笔记本列表
     * GET /notebook/list?page=1&size=10
     *
     * @param page   当前页码（默认 1）
     * @param size   每页条数（默认 10）
     * @param request 用于从 JWT 中获取当前用户 ID
     */
    @GetMapping("/list")
    public Result<PageResult<Notebook>> listNotebooks(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request) {

        Integer userId = (Integer) request.getAttribute("userId");
        if (userId == null) {
            return Result.unauthorized("用户未登录");
        }

        PageResult<Notebook> result = notebookService.getNotebooksByUserId(userId, page, size);
        return Result.success("查询成功", result);
    }
}