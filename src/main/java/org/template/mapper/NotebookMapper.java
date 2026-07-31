package org.template.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.template.model.Notebook;

import java.util.List;

/**
 * 笔记本数据访问层
 */
@Mapper
public interface NotebookMapper extends BaseMapper<Notebook> {


    @Select("SELECT n.*, (SELECT COUNT(*) FROM note WHERE note.notebook_id = n.id) AS total_note_cnt " +
            "FROM notebook n WHERE n.id = #{id}")
    Notebook selectNotebookWithNoteCount(@Param("id") Integer id);

    @Select("SELECT name,id FROM notebook WHERE user_id = #{userId} ORDER BY sort_order ASC, created_at DESC")
    public List<Notebook> selectNotebookAbInfoByUserId(@Param("userId") Integer userId);
}
