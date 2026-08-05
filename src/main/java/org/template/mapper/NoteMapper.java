package org.template.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.template.model.Note;
import org.template.model.NoteBlock;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 笔记数据访问层
 */
@Mapper
public interface NoteMapper extends BaseMapper<Note> {

    IPage<Note> selectNotePageWithTags(Page<Note> page, @Param("notebookId") Integer notebookId);

    IPage<Note> selectNotePageWithTagsByUserId(Page<Note> page, @Param("userId") Integer userId);

    Note selectNoteWTagsByNoteId(@Param("noteId") Integer noteId);

    /**
     * 获取笔记，并加锁
     * @param noteId 笔记id
     * @return 笔记
     */
    @Select("select * from note where id = #{noteId} and is_delete = 0 FOR UPDATE")
    Note selectNoteById(@Param("noteId") Integer noteId);

    /**
     * 乐观锁软删除笔记（手动管理 SQL 以绕过 @TableLogic 对 updateById 的排除）
     * @param id        笔记 ID
     * @param version   乐观锁版本号
     * @param deletedAt 删除时间
     * @return 影响行数（0 表示版本冲突或记录不存在）
     */
    @Update("UPDATE note SET is_delete = 1, deleted_at = #{deletedAt}, version = version + 1, updated_at = #{deletedAt} WHERE id = #{id} AND version = #{version}")
    Integer deleteNoteWithOptLock(@Param("id") Integer id, @Param("version") Integer version, @Param("deletedAt") LocalDateTime deletedAt);

    /**
     * 原子性的操作：将笔记移动到指定笔记本,同时检查对应用户有米有该笔记本
     * @param noteId 笔记id
     * @param notebookId 笔记本的id
     * @return 返回影响的行数，如果失败返回0
     */
    @Update("update note set notebook_id = #{notebookId} where id=#{noteId} and (#{notebookId} is null or exists(select 1 from notebook where id = #{notebookId} and user_id = #{userId}))")
    Integer updateNoteNoteBook(
            @Param("noteId") Integer noteId,
            @Param("notebookId") Integer notebookId,
            @Param("userId") Integer userId
    );
}
