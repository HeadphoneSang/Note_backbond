package org.template.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.template.model.Note;
import org.template.model.vo.PageResult;
import org.template.model.vo.Result;
import org.template.service.NoteService;

import jakarta.servlet.http.HttpServletRequest;
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
        PageResult<Note> result = noteService.getNotesByNotebookId0(notebookId, page, size);
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
    @GetMapping("/notes")
    public Result<PageResult<Note>> listNotesAll(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request
    ) {
        Integer userId = (Integer) request.getAttribute("userId");
        if (userId == null) {
            return Result.unauthorized("用户未登录");
        }
        PageResult<Note> result = noteService.getNotesByUserId0(userId, page, size);
        if(result==null)
            return Result.error("笔记本为空");
        return Result.success("查询成功", result);
    }

    @GetMapping("/getNote")
    public Result<Note> getNote(
            @RequestParam Integer noteId
    ) {
        Note note = noteService.getNoteWTags(noteId);
        if(note==null)
            return Result.error("笔记不存在");
        return Result.success("查询成功", note);
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

    @PostMapping("/title")
    public Result<Note> updateNoteTitle(
            @RequestParam Integer noteId,
            @RequestParam String title,
            @RequestParam Integer version) {
        Note note = new Note();
        note.setTitle(title);
        note.setId(noteId);
        note.setVersion(version);
        // 增加新的修改时间的更新逻辑
        Note result = noteService.updateNoteWithOptLock(note);
        return result == null ? Result.error("笔记不存在") : Result.success(result);
    }

    @PostMapping("/summary")
    public Result<Note> updateNoteSummary(
            @RequestParam Integer noteId,
            @RequestParam String summary,
            @RequestParam Integer version) {
        Note note = new Note();
        note.setSummary(summary);
        note.setId(noteId);
        note.setVersion(version);
        // 添加新的修改时间的更新逻辑
        Note result = noteService.updateNoteWithOptLock(note);
        return result == null ? Result.error("笔记不存在") : Result.success(result);
    }

    @PostMapping("/mark")
    public Result<Note> updateNoteMarked(
            @RequestParam Integer noteId,
            @RequestParam Boolean isMarked,
            @RequestParam Integer version) {
        Note result = noteService.markNote(noteId, isMarked, version);
        return result == null ? Result.error("笔记不存在") : Result.success(result);
    }

    @PostMapping("/delete")
    public Result<String> deleteNote(
            @RequestParam Integer noteId,
            @RequestParam Integer version) {
        if (noteService.deleteNote(noteId, version)) {
            return Result.success("删除成功");
        }
        return Result.error("删除失败");
    }

    @PostMapping("/editNotebook")
    public Result<String> updateNoteNoteBook(
            @RequestParam Integer noteId,
            @RequestParam Integer notebookId,
            HttpServletRequest request) {
        Integer userId = (Integer) request.getAttribute("userId");
        if (userId == null) {
            return Result.unauthorized("用户未登录");
        }
        // 如果是notebookId=-1，就更新id为null
        notebookId = notebookId == -1 ? null : notebookId;
        if(noteService.updateNoteNotebook(noteId, notebookId, userId)){
            return Result.success("修改成功");
        }else{
            return Result.error("修改失败,笔记无需移动");
        }
    }
}