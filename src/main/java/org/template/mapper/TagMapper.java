package org.template.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;
import org.template.model.Tag;
import org.template.model.dto.NoteTagDTO;

import java.util.List;

/**
 * 标签数据访问层
 */
@Mapper
public interface TagMapper extends BaseMapper<Tag> {

    @Select("SELECT n.`tag_id` AS id, t.`name` AS NAME , t.`color` AS color, t.`user_id` AS user_id, t.`created_at` AS created_at FROM note_tag n LEFT JOIN tag t ON n.`tag_id` = t.`id` WHERE n.note_id = #{noteId}")
    List<Tag> listByNoteId(Integer noteId);

    @Insert("INSERT IGNORE INTO tag (user_id,name,color) values(#{tag.userId},#{tag.name},#{tag.color})")
    @Options(useGeneratedKeys = true, keyProperty = "tag.id")
    Integer insertTagIfNotExist(@Param("tag") Tag tag);

    List<NoteTagDTO> queryTagsByNoteIds(@Param("noteIds") List<Integer> noteIds);
}
