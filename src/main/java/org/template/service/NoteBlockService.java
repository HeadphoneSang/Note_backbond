package org.template.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.executor.BatchResult;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.template.exceptions.OptimisticLockException;
import org.template.mapper.NoteBlockMapper;
import org.template.model.NoteBlock;
import org.template.model.dto.NoteDiffRequest;

import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

@Service
public class NoteBlockService {

    @Autowired
    private NoteBlockMapper blockMapper;

    @Autowired
    @Qualifier("sqlSessionTemplateBatch")
    private SqlSessionTemplate sqlSessionTemplate;

    @Autowired
    private BlockRedisService blockRedisService;

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void insertNoteBlocks(List<NoteBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) return;

        try {
            // 直接执行插入即可，若冲突会直接砸出 DuplicateKeyException
            blockMapper.batchInsertBlock(blocks);
        } catch (DuplicateKeyException e) {
            // 1. 精确捕获唯一键冲突
            // 2. 传入 e，保留原始异常堆栈信息
            throw new OptimisticLockException("插入笔记 ID 冲突");
        }
    }


    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public List<String> updateNoteBlocks(List<NoteBlock> blocks){
        if (blocks==null || blocks.isEmpty())   return new ArrayList<>();
        NoteBlockMapper batchMapper = sqlSessionTemplate.getMapper(NoteBlockMapper.class);
        for (NoteBlock block : blocks) {
            batchMapper.updateById(block);
        }
        List<BatchResult> results = sqlSessionTemplate.flushStatements();
        int[] updatedCounts = results.get(0).getUpdateCounts();
        List< String> failedBlockIds = new ArrayList<>();
        for (int i = 0; i < updatedCounts.length; i++) {
            int count = updatedCounts[i];
            if (count ==0 || count == Statement.EXECUTE_FAILED)
                failedBlockIds.add(blocks.get(i).getId());
        }
        return failedBlockIds;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW,rollbackFor = Exception.class)
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
        return blockRedisService.getNoteBlocks(
                noteId,
                ()->blockMapper.selectList(new LambdaQueryWrapper<NoteBlock>().eq(NoteBlock::getNoteId, noteId).orderByAsc(NoteBlock::getOrderKey)),
                (dirtyIds)->blockMapper.selectByIds(dirtyIds)
                );
    }
}
