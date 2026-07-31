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
    @Select("select * from note where id = #{noteId} FOR UPDATE")
    Note selectNoteById(@Param("noteId") Integer noteId);

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
