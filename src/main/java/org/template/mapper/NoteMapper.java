package org.template.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.template.model.Note;

import java.util.List;

/**
 * 笔记数据访问层
 */
@Mapper
public interface NoteMapper extends BaseMapper<Note> {

    IPage<Note> selectNotePageWithTags(Page<Note> page, @Param("notebookId") Integer notebookId);

    IPage<Note> selectNotePageWithTagsByUserId(Page<Note> page, @Param("userId") Integer userId);
}
