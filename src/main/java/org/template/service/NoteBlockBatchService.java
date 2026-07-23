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
        // 处理增量更新事件
        if(!req.getUpdatedBlocks().isEmpty()){
            try{
                blockService.updateNoteBlocks(req.getUpdatedBlocks());
            }catch (OptimisticLockException e){
                // 获得没有更新成功的块的ids
                List<NoteBlock> expectBlocks = new ArrayList<>(req.getUpdatedBlocks());
                expectBlocks.forEach(block->block.setVersion(block.getVersion()+1));
                Set<String> sucBlockIds = new HashSet<>(blockMapper.selectByIdAndVersion(expectBlocks));
                List<String> failUpdateIds = req.getUpdatedBlocks().stream().map(NoteBlock::getId).filter(id ->!sucBlockIds.contains(id)).collect(Collectors.toList());
                result.setFailUpdateBlocks(failUpdateIds);
            }
        }
        // 处理删除事件
        if(!req.getDeletedBlocks().isEmpty()){
            try{
                blockService.deleteNoteBlocks(req.getDeletedBlocks());
            }catch (OptimisticLockException e){
                // 获得没有删除成功的块的ids
                List<String> allIds = req.getDeletedBlocks().stream().map(NoteBlock::getId).collect(Collectors.toList());
                List<String> failedBlockIds = blockMapper.selectBatchIds(allIds).stream().map(NoteBlock::getId).collect(Collectors.toList());
                result.setFailDeletedBlocks(failedBlockIds);
            }
        }
        return result;
    }
}
