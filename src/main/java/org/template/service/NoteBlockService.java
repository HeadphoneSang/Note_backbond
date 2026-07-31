package org.template.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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

    /**
     * 获取指定笔记下面的笔记块
     * @param noteId 笔记的id
     * @return 返回块集合
     */
    public List<NoteBlock> selectNoteBlocks(Integer noteId){
        return blockMapper.selectList(new LambdaQueryWrapper<NoteBlock>().eq(NoteBlock::getNoteId, noteId).orderByAsc(NoteBlock::getOrderKey));
    }

//    /**
//     * 批量移动块的笔记id，如果移动后，发现有没有移动的块，就撤回
//     * @param oldNoteId 旧笔记id
//     * @param newNoteId 新笔记id
//     * @return 移动是否成功
//     */
//    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
//    public boolean updateNoteBlockNoteId(Integer oldNoteId, Integer newNoteId){
//        Integer updatedCount = blockMapper.updateBlockNoteId(oldNoteId, newNoteId);
//        // 检查是否还有没有更新的块，如果没有就回滚事务
//        List<String> notModifiedIds = blockMapper.selectIdsByNoteId(oldNoteId);
//        if (!notModifiedIds.isEmpty()){
//            throw new OptimisticLockException("移动笔记内容出错!已撤销");
//        }
//        return true;
//    }
}
