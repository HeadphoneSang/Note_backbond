package org.template.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
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

    Integer batchDeleteBlockWithOptLock(@Param("list") List<NoteBlock> list);

    Integer batchUpdateBlockWithOptLock(@Param("blocks") List<NoteBlock> blocks);

    Integer batchInsertBlock(@Param("blocks") List<NoteBlock> blocks);
}
