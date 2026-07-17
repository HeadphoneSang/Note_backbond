package org.template.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.template.model.Note;
import org.template.model.vo.PageResult;
import org.template.model.vo.Result;
import org.template.service.NoteService;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;

/**
 * 笔记控制器 — 笔记的查询与操作
 */
@RestController
@RequestMapping("/note")
public class NoteController {

    @Autowired
    private NoteService noteService;

    /**
     * 分页查询指定笔记本下的笔记列表
     * GET /note/list?page=1&size=10&notebookId=1
     *
     * @param page       当前页码（默认 1）
     * @param size       每页条数（默认 10）
     * @param notebookId 笔记本 ID（必填）
     */
    @GetMapping("/list")
    public Result<PageResult<Note>> listNotes(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam int notebookId) {
        PageResult<Note> result = noteService.getNotesByNotebookId(notebookId, page, size);
        if(result==null)
            return Result.error("笔记本为空");
        return Result.success("查询成功", result);
    }

    /**
     * 分页查询指定用户的的笔记列表
     * GET /note/listAll?page=1&size=10&userId=1
     *
     * @param page       当前页码（默认 1）
     * @param size       每页条数（默认 10）
     */
    @GetMapping("/listAll")
    public Result<PageResult<Note>> listNotesAll(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request
    ) {
        Integer userId = (Integer) request.getAttribute("userId");
        if (userId == null) {
            return Result.unauthorized("用户未登录");
        }
        PageResult<Note> result = noteService.getNotesByUserId(userId, page, size);
        if(result==null)
            return Result.error("笔记本为空");
        return Result.success("查询成功", result);
    }

    /**
     * 创建笔记
     * POST /note/create
     *
     * @param note 笔记对象
     * @return 创建成功的笔记 ID
     */
    @PostMapping("/create")
    public Result<Note> createNote(@RequestBody Note note, HttpServletRequest request) {
        Integer userId = (Integer) request.getAttribute("userId");
        if (userId == null) {
            return Result.unauthorized("用户未登录");
        }
        note.setUserId(userId);

        // 默认标题：未命名笔记 + 随机后缀
        if (note.getTitle() == null || note.getTitle().isEmpty()) {
            note.setTitle("未命名笔记" + UUID.randomUUID().toString().substring(0, 8));
        }
        // 默认摘要
        if (note.getSummary() == null || note.getSummary().isEmpty()) {
            note.setSummary("无摘要");
        }
        // 默认版本号
        note.setVersion(1);
        Note created = noteService.createNote(note);
        return Result.success(created);
    }
}