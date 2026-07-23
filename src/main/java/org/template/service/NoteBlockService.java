package org.template.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.template.exceptions.OptimisticLockException;
import org.template.mapper.NoteBlockMapper;
import org.template.model.NoteBlock;
import org.template.model.dto.NoteDiffRequest;

import java.util.List;

@Service
public class NoteBlockService {

    @Autowired
    private NoteBlockMapper blockMapper;

    @Transactional(propagation = Propagation.NESTED, rollbackFor = Exception.class)
    public void insertNoteBlocks(List<NoteBlock> blocks){
        if (blockMapper.batchInsertBlock(blocks)< blocks.size())
            throw new OptimisticLockException("批量插入笔记块失败,由用户修改了当前内容");
    }


    public void updateNoteBlocks(List<NoteBlock> blocks){
        if (blockMapper.batchUpdateBlockWithOptLock(blocks)< blocks.size())
            throw new OptimisticLockException("批量更新笔记块出现冲突,由用户修改了当前内容");
    }

    public void deleteNoteBlocks(List<NoteBlock> blocks){
        if (blockMapper.batchDeleteBlockWithOptLock( blocks)< blocks.size())
            throw new OptimisticLockException("批量删除笔记块出现冲突,由用户修改了当前内容");
    }
}
