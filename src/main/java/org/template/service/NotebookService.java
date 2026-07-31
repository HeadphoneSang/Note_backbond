package org.template.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.template.mapper.NotebookMapper;
import org.template.model.Notebook;
import org.template.model.vo.PageResult;

import java.util.ArrayList;
import java.util.List;

/**
 * 笔记本业务逻辑层
 */
@Service
public class NotebookService {

    @Autowired
    private NotebookMapper notebookMapper;

    /**
     * 创建笔记本（同名自动加后缀）
     */
    public Notebook addNotebook(String name, String description, Integer userId) {
        // 若同名笔记本已存在，自动追加 (1)、(2)… 后缀
        String finalName = resolveNotebookName(userId, name);

        Notebook notebook = new Notebook();
        notebook.setName(finalName);
        notebook.setDescription(description);
        notebook.setUserId(userId);
        notebookMapper.insert(notebook);
        return notebook;
    }

    /**
     * 分页查询指定用户的笔记本列表（按 sortOrder 升序，创建时间倒序）
     *
     * @param userId  用户 ID
     * @param current 当前页码（从 1 开始）
     * @param size    每页条数
     * @return 分页结果
     */

    public PageResult<Notebook> getNotebooksByUserId(Integer userId, long current, long size) {
        Page<Notebook> page = new Page<>(current, size);
        LambdaQueryWrapper<Notebook> wrapper = new LambdaQueryWrapper<Notebook>()
                .eq(Notebook::getUserId, userId)
                .orderByAsc(Notebook::getSortOrder)
                .orderByDesc(Notebook::getCreatedAt);

        notebookMapper.selectPage(page, wrapper);
        return PageResult.of(page);
    }

    /**
     * 解析可用的笔记本名称：同名已存在时自动加 (n) 后缀
     */
    private String resolveNotebookName(Integer userId, String name) {
        // 先检查原名是否可用
        if (!existsByName(userId, name)) {
            return name;
        }

        // 原名已占用，从 (1) 开始递增直到找到可用名称
        int n = 1;
        String candidate;
        do {
            candidate = name + " (" + n + ")";
            n++;
        } while (existsByName(userId, candidate));

        return candidate;
    }

    /**
     * 查询同用户下是否存在指定名称的笔记本
     */
    private boolean existsByName(Integer userId, String name) {
        Long count = notebookMapper.selectCount(
                new LambdaQueryWrapper<Notebook>()
                        .eq(Notebook::getUserId, userId)
                        .eq(Notebook::getName, name)
        );
        return count > 0;
    }

    /**
     * 根据 ID 查询笔记本
     */
    public Notebook getNotebookById(Integer notebookId) {
        return notebookMapper.selectNotebookWithNoteCount(notebookId);
    }

    /**
     * 查询指定用户的所有笔记本简略信息
     * @param userId 用户ID
     * @return 笔记本简略信息
     */
    public List<Notebook> selectNotebookAbInfoByUserId(Integer userId){
        List<Notebook> results = notebookMapper.selectNotebookAbInfoByUserId(userId);
        if (results == null) {
            return new ArrayList<>();
        }
        return results;
    }
}