package org.template.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.template.exceptions.NoteBlockConflictException;
import org.template.model.NoteBlock;
import org.template.model.dto.NoteDiffRequest;
import org.template.model.dto.NoteDiffResult;
import org.template.model.vo.Result;
import org.template.service.NoteBlockBatchService;
import org.template.service.NoteBlockService;

import java.util.List;

@RestController
@RequestMapping("/noteBlock")
public class NoteBlockController {

    @Autowired
    private NoteBlockService noteBlockService;

    @Autowired
    private NoteBlockBatchService noteBlockBatchService;

    @PostMapping("batchUpdate")
    public Result<NoteDiffResult> batchUpdate(@RequestBody NoteDiffRequest req) {
        NoteDiffResult result = noteBlockBatchService.updateNoteBlocksDiff(req);
        return Result.success(result.hasConflict() ? "出现更新冲突!请重新刷新页面" : "更新成功", result);
    }

    @GetMapping("getBlocks")
    public Result<List<NoteBlock>> getBlocks(Integer noteId) {
        List<NoteBlock> blocks = noteBlockService.selectNoteBlocks(noteId);
        return Result.success("获取成功", blocks); 
    }
}
