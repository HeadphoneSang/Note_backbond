package org.template.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.template.exceptions.OptimisticLockException;
import org.template.mapper.NoteBlockMapper;
import org.template.model.NoteBlock;
import org.template.model.dto.NoteDiffRequest;
import org.template.model.dto.NoteDiffResult;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class NoteBlockBatchService {

    @Autowired
    private NoteBlockMapper blockMapper;

    @Autowired
    private NoteBlockService blockService;

    @Autowired
    private BlockRedisService blockRedisService;

    /**
     * 传入要插入的块的id集合，返回没有插入成功的id集合
     * @param allIds 所有的插入的id集合
     * @return 返回没有插入成功的id集合
     */
    private List<String> extractNotExistIds(List<String> allIds){
        List<NoteBlock> sucBlocks = blockMapper.selectBatchIds(allIds);
        Set<String> existedIds = sucBlocks.stream().map(NoteBlock::getId).collect(Collectors.toSet());
        return allIds.stream().filter(id -> !existedIds.contains(id)).collect(Collectors.toList());
    }

    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public NoteDiffResult updateNoteBlocksDiff(NoteDiffRequest req){
        NoteDiffResult result = new NoteDiffResult();
        // 处理insert事件
        if(!req.getInsertedBlocks().isEmpty()){
            try{
                blockService.insertNoteBlocks(req.getInsertedBlocks());
            }catch (OptimisticLockException e){
                //插入如果失败了，就所有的插入操作回滚
                List<String> notExistIds = extractNotExistIds(req.getInsertedBlocks().stream().map(NoteBlock::getId).collect(Collectors.toList()));
                result.setFailCreatedBlocks(notExistIds);
            }
        }
        if(!req.getInsertedBlocks().isEmpty() && result.getFailCreatedBlocks().isEmpty()){
            // 没有插入失败的，就是全部插入成功，更新redis
            System.out.println("缓存插入redis:"+req.getInsertedBlocks().size());
            int noteId = req.getInsertedBlocks().get(0).getNoteId();
            blockRedisService.cacheNewBlocks(noteId,req.getInsertedBlocks());
        }
        // 处理增量更新事件
        List<NoteBlock> updatedBlocks = req.getUpdatedBlocks();
        if(!req.getUpdatedBlocks().isEmpty()){
//            System.out.println("批量更新前的versions:");
//            System.out.println(req.getUpdatedBlocks().stream().map(NoteBlock::getVersion).toList());
            final List<String> failedBlockIds = blockService.updateNoteBlocks(req.getUpdatedBlocks());
//            System.out.println("批量更新后的versions:");
//            System.out.println(req.getUpdatedBlocks().stream().map(NoteBlock::getVersion).toList());
            if (!failedBlockIds.isEmpty()){
                result.setFailUpdateBlocks(failedBlockIds);
                updatedBlocks = updatedBlocks.stream().filter(noteBlock -> !failedBlockIds.contains(noteBlock.getId())).toList();
            }
        }
//        System.out.println("需要缓存的updatedBlocks的versions:");
//        System.out.println(updatedBlocks.stream().map(NoteBlock::getVersion).toList());
        if(!updatedBlocks.isEmpty()){
            // 更新redis
            System.out.println("缓存更新redis:"+updatedBlocks.size());
            int noteId = updatedBlocks.get(0).getNoteId();
            blockRedisService.dirtyBlocks(noteId,updatedBlocks);
        }
        // 处理删除事件
        if(!req.getDeletedBlocks().isEmpty()){
            try{
                blockService.deleteNoteBlocks(req.getDeletedBlocks());
            }catch (OptimisticLockException e){
                // 获得没有删除成功的块的ids
                List<String> allIds = req.getDeletedBlocks().stream().map(NoteBlock::getId).collect(Collectors.toList());
                List<String> failedBlockIds = blockMapper.selectBatchIds(allIds).stream().map(NoteBlock::getId).toList();
                result.setFailDeletedBlocks(failedBlockIds);
            }
        }
        if(!req.getDeletedBlocks().isEmpty() &&result.getFailDeletedBlocks().isEmpty()){
            //  删除没有错误，全删除，更新redis
            int noteId = req.getDeletedBlocks().get(0).getNoteId();
            System.out.println("删除缓存:"+req.getDeletedBlocks().size());
            blockRedisService.deleteBlocks(noteId,req.getDeletedBlocks());
        }
        return result;
    }
}
