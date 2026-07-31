package org.template.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.template.model.NoteTag;

import java.util.List;

public interface NoteTagMapper extends BaseMapper<NoteTag> {


    Integer insertBatchIgnores(List<NoteTag> noteTagList);

    Integer deleteBatch(List<NoteTag> noteTagList);

    /**
     * 从表中查找属于noteTagList的记录
     * @param noteTagList 查询集合
     * @return 返回属于这个集合的记录
     */
    List<NoteTag> selectByNoteTag(List<NoteTag> noteTagList);
}
