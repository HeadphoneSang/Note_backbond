package org.template.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.template.mapper.TagMapper;
import org.template.model.Tag;
import org.template.model.dto.NoteTagDTO;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TagService {

    @Autowired
    private TagMapper tagMapper;

    public List<Tag> list(int userId) {
        return tagMapper.selectList(new QueryWrapper<Tag>().eq("user_id", userId));
    }

    public List<Tag> listByNoteId(Integer noteId) {
        return tagMapper.listByNoteId(noteId);
    }

    public Tag insert(Tag tag) {
        if(tagMapper.insertTagIfNotExist(tag)>0)
            return tag;
        return null;
    }

    /**
     * 查询笔记的标签列表
     * 每个笔记的标签列表，按照标签跟笔记的关系的最新修改事件顺序进行升序排序(越新添加的标签越靠后)
     * @param noteIds 笔记ID列表
     * @return 笔记ID对应的标签列表
     */
    public Map<Integer,List<Tag>> queryTagsByNoteIds(List<Integer> noteIds) {
        // 返回的不一定和noteIds一一对应，比如某个notedId对应的tag不存在，那么返回的tags中就没有这个notedId对应的tag列表
        List<NoteTagDTO> flatTags = tagMapper.queryTagsByNoteIds(noteIds);
        Map<Integer,List<Tag>> map = flatTags.stream().filter(tag -> tag.getTagId() != null).collect(
                Collectors.groupingBy(
                        NoteTagDTO::getNoteId, //key
                        Collectors.mapping(noteTagDTO -> {
                            Tag tag = new Tag();
                            tag.setId(noteTagDTO.getTagId());
                            tag.setName(noteTagDTO.getTagName());
                            tag.setColor(noteTagDTO.getTagColor());
                            tag.setUserId(noteTagDTO.getUserId());
                            return tag;
                        },Collectors.toList())
                        )
        );
        noteIds.forEach(noteId -> {
            if(!map.containsKey(noteId))
                map.put(noteId,new java.util.ArrayList<>());
        });
        return map;
    }
}
