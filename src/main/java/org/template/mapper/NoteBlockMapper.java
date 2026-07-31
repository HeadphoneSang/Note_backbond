package org.template.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.template.model.NoteBlock;

import java.util.List;

public interface NoteBlockMapper extends BaseMapper<NoteBlock> {

    Integer batchDeleteBlockWithOptLock(@Param("blocks") List<NoteBlock> blocks);
    Integer batchUpdateBlockWithOptLock(@Param("blocks") List<NoteBlock> blocks);

    Integer batchInsertBlock(@Param("blocks") List<NoteBlock> blocks);

    List<String> selectByIdAndVersion(@Param("blocks") List<NoteBlock> blocks);

    @Update("update note_block set note_id = #{newNoteId} where note_id = #{oldNoteId}")
    Integer updateBlockNoteId(@Param("oldNoteId") Integer oldNoteId, @Param("newNoteId") Integer newNoteId);

    @Select("select id from note_block where note_id = #{noteId}")
    List<String> selectIdsByNoteId(@Param("noteId") Integer noteId);
}
