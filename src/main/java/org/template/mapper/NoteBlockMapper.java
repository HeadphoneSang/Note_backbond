package org.template.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.template.model.NoteBlock;

import java.util.List;

public interface NoteBlockMapper extends BaseMapper<NoteBlock> {

    Integer batchDeleteBlockWithOptLock(@Param("blocks") List<NoteBlock> blocks);
    Integer batchUpdateBlockWithOptLock(@Param("blocks") List<NoteBlock> blocks);

    Integer batchInsertBlock(@Param("blocks") List<NoteBlock> blocks);

    List<String> selectByIdAndVersion(@Param("blocks") List<NoteBlock> blocks);
}
