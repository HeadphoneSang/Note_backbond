package org.template.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.template.exceptions.OptimisticLockException;
import org.template.mapper.NoteBlockMapper;
import org.template.mapper.NoteMapper;
import org.template.mapper.NotebookMapper;
import org.template.model.Note;
import org.template.model.NoteBlock;
import org.template.model.Notebook;
import org.template.model.Tag;
import org.template.model.dto.NoteDiffRequest;
import org.template.model.vo.PageResult;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 笔记业务逻辑层
 */
@Service
public class NoteService {

    @Autowired
    private NoteMapper noteMapper;

    @Autowired
    private TagService tagService;

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
    @Deprecated
    public PageResult<Note> getNotesByNotebookId(Integer notebookId, long current, long size) {
        Page<Note> page = new Page<>(current, size);
        IPage<Note> result = noteMapper.selectNotePageWithTags(page, notebookId);
        long pageCntResult = result.getTotal();
        if (pageCntResult == 0) {
            return null;
        }
        return PageResult.of( result);
    }

    private PageResult<Note> extractTags4Notes(IPage<Note> result) {
        long pageCntResult = result.getTotal();
        if (pageCntResult == 0) {
            return null;
        }
        List<Integer> ids = result.getRecords().stream().map(Note::getId).collect(Collectors.toList());
        Map<Integer,List<Tag>> noteTagsMap = tagService.queryTagsByNoteIds(ids);
        result.getRecords().forEach(note -> note.setTags(noteTagsMap.get(note.getId())));
        return PageResult.of( result);
    }

    public PageResult<Note> getNotesByNotebookId0(Integer notebookId, long current, long size) {
        Page<Note> page = new Page<>(current, size);
        IPage<Note> result = noteMapper.selectPage(page,new LambdaQueryWrapper<Note>().eq(Note::getNotebookId, notebookId).orderByAsc(Note::getSortOrder).orderByDesc(Note::getUpdatedAt));
        return extractTags4Notes(result);
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
    @Deprecated
    public PageResult<Note> getNotesByUserId(Integer userId, long current, long size) {
        Page<Note> page = new Page<>(current, size);
        IPage<Note> result = noteMapper.selectNotePageWithTagsByUserId(page, userId);
        long pageCntResult = result.getTotal();
        if (pageCntResult == 0) {
            return null;
        }
        return PageResult.of( result);
    }

    /**
     * 分页查询指定用户下的笔记列表
     * <p>
     * 仅返回当前用户、未删除的笔记，按 sortOrder 升序、创建时间倒序排列。
     * 每个笔记中包含标签列表，标签列表按创建时间升序排序。
     * @param userId      用户 ID
     * @param current    当前页码（从 1 开始）
     * @param size       每页条数
     * @return 分页结果
     */
    public PageResult<Note> getNotesByUserId0(Integer userId, long current, long size) {
        Page<Note> page = new Page<>(current, size);
        IPage<Note> result = noteMapper.selectPage(page, new LambdaQueryWrapper<Note>().eq(Note::getUserId, userId).eq(Note::getIsDelete, false).orderByAsc(Note::getSortOrder).orderByDesc(Note::getUpdatedAt));
        System.out.println(result.getTotal());
        return extractTags4Notes(result);
    }

    public Note createNote(Note note) {
        noteMapper.insert(note);
        return note;
    }

    public Note getNoteWTags(Integer noteId){
        return noteMapper.selectNoteWTagsByNoteId(noteId);
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

    /**
     * 将笔记移动到指定的笔记本
     * <p>
     * 执行前会检查目标笔记本是否存在（防止被删除后仍可移入），
     * 并验证笔记与笔记本均属于当前用户。
     * 使用乐观锁（@Version）保证并发安全，发生冲突时抛出 OptimisticLockException 触发事务回滚。
     *
     * @param noteId     笔记 ID
     * @param notebookId 目标笔记本 ID
     * @param userId     当前用户 ID（用于所有权校验）
     * @return 更新后的笔记（含最新版本号）
     * @throws IllegalArgumentException  笔记/笔记本不存在或无权操作
     * @throws OptimisticLockException   乐观锁版本冲突
     */
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public boolean updateNoteNotebook(Integer noteId, Integer notebookId, Integer userId) {
        // 锁住当前笔记所在的行
        Note note = noteMapper.selectNoteById(noteId);
        if (note == null) {
            throw new IllegalArgumentException("笔记不存在");
        }

        // 1. 如果已在目标笔记本中，直接返回（无需更新）幂等处理
        if (note.getNotebookId() != null && note.getNotebookId().equals(notebookId)) {
            return false;
        }

        // 2. 更新笔记，同时检查笔记本是否存在，是否属于该用户
        if(noteMapper.updateNoteNoteBook(noteId, notebookId, userId)==0){
            throw new IllegalArgumentException("笔记本不存在或无权操作");
        }

        // 3. 返回最新数据（含新版本号）
        return true;
    }

    /**
     * 切换笔记收藏状态（乐观锁，返回更新后的完整 Note）
     *
     * @param noteId   笔记 ID
     * @param isMarked 目标收藏状态
     * @param version  当前版本号（乐观锁）
     * @return 更新后的 Note（含最新版本号）
     * @throws OptimisticLockException 乐观锁版本冲突
     */
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public Note markNote(Integer noteId, boolean isMarked, Integer version) {
        Note note = new Note();
        note.setId(noteId);
        note.setIsMarked(isMarked);
        note.setVersion(version);
        note.setUpdatedAt(LocalDateTime.now());
        if (noteMapper.updateById(note) <= 0) {
            throw new OptimisticLockException("Note版本发生变化，更新冲突，已撤回: " + noteId);
        }
        return noteMapper.selectById(noteId);
    }

    /**
     * 软删除笔记（置 isDelete + deletedAt，乐观锁）
     *
     * @param noteId  笔记 ID
     * @param version 当前版本号（乐观锁）
     * @return 是否删除成功
     * @throws OptimisticLockException 乐观锁版本冲突
     */
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public boolean deleteNote(Integer noteId, Integer version) {
        LocalDateTime now = LocalDateTime.now();
        if (noteMapper.deleteNoteWithOptLock(noteId, version, now) <= 0) {
            throw new OptimisticLockException("Note版本发生变化，更新冲突，已撤回: " + noteId);
        }
        return true;
    }
}