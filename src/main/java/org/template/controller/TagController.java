package org.template.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.template.model.Tag;
import org.template.model.vo.Result;
import org.template.service.TagService;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("tag")
public class TagController {

    @Autowired
    private TagService tagService;

    @GetMapping("list")
    public Result<List<Tag>> list(HttpServletRequest request) {
        /*
        GET /tag/list
        返回当前用户的所有标签（用于 UI 中的"可添加标签"列表）
         */
        Integer userId = (Integer) request.getAttribute("userId");
        if (userId == null) {
            return Result.unauthorized("请先登录");
        }
        List<Tag> list = tagService.list(userId);
        return Result.success("获取成功", list);
    }

    @PostMapping("create")
    public Result<Tag> create(HttpServletRequest request, Tag tag) {
        /*
        POST /tag/create
        创建标签
         */
        Integer userId = (Integer) request.getAttribute("userId");
        if (userId == null) {
            return Result.unauthorized("请先登录");
        }
        tag.setUserId(userId);
        Tag result = tagService.insert(tag);
        if (result != null) {
            return Result.success("创建成功", result);
        }
        return Result.error("创建失败, 不能创建重名标签");
    }
}
