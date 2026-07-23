package org.template.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.template.exceptions.NoteBlockConflictException;
import org.template.model.dto.NoteDiffRequest;
import org.template.model.dto.NoteDiffResult;
import org.template.model.vo.Result;
import org.template.service.NoteBlockBatchService;
import org.template.service.NoteBlockService;

@RestController
@RequestMapping("/noteBlock")
public class NoteBlockController {

    @Autowired
    private NoteBlockService noteBlockService;

    @Autowired
    private NoteBlockBatchService noteBlockBatchService;

    @PostMapping("batchUpdate")
    public Result<NoteDiffResult> batchUpdate(@RequestBody NoteDiffRequest req) {
        System.out.println(req.getUpdatedBlocks().size());
        System.out.println(req.getDeletedBlocks().size());
        System.out.println(req.getInsertedBlocks().size());
        NoteDiffResult result = noteBlockBatchService.updateNoteBlocksDiff(req);
        return Result.success(result.hasConflict() ? "出现更新冲突!请重新刷新页面" : "更新成功", result);
    }
}
