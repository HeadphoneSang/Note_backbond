package org.template.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.template.model.Note;

/**
 * 笔记数据访问层
 */
@Mapper
public interface NoteMapper extends BaseMapper<Note> {

}
