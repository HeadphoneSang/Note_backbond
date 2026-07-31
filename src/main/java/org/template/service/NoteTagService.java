package org.template.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.template.exceptions.OptimisticLockException;
import org.template.mapper.NoteTagMapper;
import org.template.model.NoteBlock;
import org.template.model.NoteTag;
import org.template.model.dto.NoteTagDiffRequest;
import org.template.model.dto.NoteTagDiffResult;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class NoteTagService {

    @Autowired
    private NoteTagMapper noteTagMapper;

    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public NoteTagDiffResult updateNoteBlocksDiff(NoteTagDiffRequest req){
        NoteTagDiffResult result = new NoteTagDiffResult();
        Integer noteId = req.getNoteId();
        // 处理add
        if(!req.getAddTagIds().isEmpty()){
            List<Integer> addTags = req.getAddTagIds();
            List<NoteTag> newNoteTags = addTags.stream().map(tagId -> new NoteTag(noteId, tagId, null)).collect(Collectors.toList());
            //  插入的数量和预期的不匹配，提取拆入失败的集
            if (noteTagMapper.insertBatchIgnores(newNoteTags)!= addTags.size()){
                // 待查集 len(sucIdsList) <= len(newNoteTags)
                List<Integer> sucIdsList = noteTagMapper.selectByNoteTag(newNoteTags).stream().map(NoteTag::getTagId).collect(Collectors.toList());
                // 幂等
                if(sucIdsList.size() != newNoteTags.size()){
                    // 失败集
                    Set<Integer> failIdsList = newNoteTags.stream().map(NoteTag::getTagId).collect(Collectors.toSet());
                    sucIdsList.forEach(failIdsList::remove);
                    result.setFailAddedTags(new ArrayList<>(failIdsList));
                }
            }
        }
        // 处理remove
        if(!req.getRemoveTagIds().isEmpty()){
            List<Integer> removeTags = req.getRemoveTagIds();
            List<NoteTag> removeNoteTags = removeTags.stream().map(tagId -> new NoteTag(noteId, tagId, null)).collect(Collectors.toList());
            if (noteTagMapper.deleteBatch(removeNoteTags) != removeTags.size()){
                // 失败集
                List<Integer> failIdsList = noteTagMapper.selectByNoteTag(removeNoteTags).stream().map(NoteTag::getTagId).collect(Collectors.toList());
                result.setFailRemovedTags(new ArrayList<>(failIdsList));
            }
        }
        // 设置一个默认值，防止返回null
        result.setFailRemovedTags(result.getFailRemovedTags()==null?new ArrayList<>():result.getFailRemovedTags());
        result.setFailAddedTags(result.getFailAddedTags()==null?new ArrayList<>():result.getFailAddedTags());
        return result;
    }
}
