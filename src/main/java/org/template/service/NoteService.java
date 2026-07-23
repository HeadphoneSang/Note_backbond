package org.template.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.template.exceptions.OptimisticLockException;
import org.template.mapper.NoteBlockMapper;
import org.template.mapper.NoteMapper;
import org.template.model.Note;
import org.template.model.NoteBlock;
import org.template.model.dto.NoteDiffRequest;
import org.template.model.vo.PageResult;
import java.time.LocalDateTime;

/**
 * 笔记业务逻辑层
 */
@Service
public class NoteService {

    @Autowired
    private NoteMapper noteMapper;

    @Autowired
    private NoteBlockMapper noteBlockMapper;

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

    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public Note updateNoteWithOptLock(Note note) {
        // 更新前将 updatedAt 设为当前时间
        note.setUpdatedAt(LocalDateTime.now());
        if (noteMapper.updateById(note)<= 0){
            throw new OptimisticLockException("Note版本发生变化，更新冲突，已撤回: "+note.getId());
        }
        return noteMapper.selectById(note.getId());
    }
}