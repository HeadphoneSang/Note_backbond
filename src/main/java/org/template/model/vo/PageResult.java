package org.template.model.vo;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;

/**
 * 分页响应对象
 * 包装 MyBatis-Plus 分页结果，返回统一的分页结构
 */
public class PageResult<T> {

    /** 当前页码（从 1 开始） */
    private long current;

    /** 每页条数 */
    private long size;

    /** 总记录数 */
    private long total;

    /** 总页数 */
    private long pages;

    /** 当前页数据 */
    private List<T> records;

    private PageResult() {}

    /**
     * 从 MyBatis-Plus IPage 构建 PageResult
     */
    public static <T> PageResult<T> of(IPage<T> page) {
        PageResult<T> result = new PageResult<>();
        result.current = page.getCurrent();
        result.size = page.getSize();
        result.total = page.getTotal();
        result.pages = page.getPages();
        result.records = page.getRecords();
        return result;
    }

    // ============ Getter / Setter ============

    public long getCurrent() { return current; }
    public void setCurrent(long current) { this.current = current; }

    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }

    public long getTotal() { return total; }
    public void setTotal(long total) { this.total = total; }

    public long getPages() { return pages; }
    public void setPages(long pages) { this.pages = pages; }

    public List<T> getRecords() { return records; }
    public void setRecords(List<T> records) { this.records = records; }
}