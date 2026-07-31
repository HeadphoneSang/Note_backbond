package org.template.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.template.model.Tag;
import org.template.model.dto.NoteTagDiffRequest;
import org.template.model.dto.NoteTagDiffResult;
import org.template.model.vo.Result;
import org.template.service.NoteTagService;
import org.template.service.TagService;

import java.util.List;

@RestController
@RequestMapping("/noteTag")
public class NoteTagController {

    @Autowired
    private TagService tagService;

    @Autowired
    private NoteTagService noteTagService;

    @GetMapping("/list")
    /*
     GET /noteTag/list?noteId={noteId}
     获取笔记的标签列表
    */
    public Result<List<Tag>> list(@RequestParam("noteId") Integer noteId) {
        if (noteId == null) {
            return Result.error("参数错误");
        }
        List<Tag> list = tagService.listByNoteId(noteId);
        return Result.success("获取成功", list);
    }

    @PostMapping("/batch")
    /*
     POST /noteTag/batch
        Body: {
          noteId: number,
          addTagIds: number[],      // 要关联的已有标签 ID
          removeTagIds: number[],   // 要取消关联的标签 ID
          newTags: [{               // 新建并关联的标签
            name: string,
            color?: string,
            tempId: string          // 前端临时 ID，用于映射回真实 ID
          }]
        }
      批量提交标签变更
      返回 NoteTagDiffResult，包含：
        createdTagIds: Map<tempId, realId> — 新创建的标签的临时 ID → 真实 ID 映射
        hasConflict: boolean — 是否有部分操作失败
        failCreatedTags, failAddedTags, failRemovedTags — 失败项列表
        支持版本冲突（409）
    */
    public Result<NoteTagDiffResult> batch(@RequestBody NoteTagDiffRequest request) {
        Integer noteId = request.getNoteId();
        if (noteId == null) {
            return Result.error("参数错误");
        }
        NoteTagDiffResult result = noteTagService.updateNoteBlocksDiff(request);
        if (result.hasConflict()) {
            return Result.error(409, "出现更新冲突!请重新刷新页面", result);
        }
        return Result.success("更新成功", result);
    }

}
