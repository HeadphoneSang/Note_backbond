package org.template.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.template.mapper.NoteMapper;
import org.template.model.Note;
import org.template.model.vo.PageResult;

/**
 * 笔记业务逻辑层
 */
@Service
public class NoteService {

    @Autowired
    private NoteMapper noteMapper;

    /**
     * 分页查询指定笔记本下的笔记列表
     * <p>
     * 仅返回当前用户、未删除的笔记，按 sortOrder 升序、创建时间倒序排列。
     *
     * @param notebookId 笔记本 ID
     * @param current    当前页码（从 1 开始）
     * @param size       每页条数
     * @return 分页结果
     */
    public PageResult<Note> getNotesByNotebookId(Integer notebookId, long current, long size) {
        Page<Note> page = new Page<>(current, size);
        IPage<Note> result = noteMapper.selectNotePageWithTags(page, notebookId);
        long pageCntResult = result.getTotal();
        if (pageCntResult == 0) {
            return null;
        }
        return PageResult.of( result);
    }

    /**
     * 分页查询指定笔记本下的笔记列表
     * <p>
     * 仅返回当前用户、未删除的笔记，按 sortOrder 升序、创建时间倒序排列。
     *
     * @param userId      用户 ID
     * @param current    当前页码（从 1 开始）
     * @param size       每页条数
     * @return 分页结果
     */
    public PageResult<Note> getNotesByUserId(Integer userId, long current, long size) {
        Page<Note> page = new Page<>(current, size);
        IPage<Note> result = noteMapper.selectNotePageWithTagsByUserId(page, userId);
        long pageCntResult = result.getTotal();
        if (pageCntResult == 0) {
            return null;
        }
        return PageResult.of( result);
    }

    public Note createNote(Note note) {
        noteMapper.insert(note);
        return note;
    }
}