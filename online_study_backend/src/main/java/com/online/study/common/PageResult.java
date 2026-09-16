package com.online.study.common;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 统一分页结果
 *
 * <p>为什么不直接把 MyBatis-Plus 的 {@code IPage} 返回给前端：
 * {@code IPage} 实现类里还带着 {@code orders}、{@code optimizeCountSql}、
 * {@code searchCount} 等一堆前端根本用不到的字段，属于接口契约污染。
 * 这里只暴露前端真正需要的 5 个字段。
 *
 * @param <T> 列表元素类型
 */
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 当前页数据 */
    private List<T> records;

    /** 总条数 */
    private long total;

    /** 当前页码（从 1 开始） */
    private long current;

    /** 每页条数 */
    private long size;

    /** 总页数 */
    private long pages;

    public PageResult() {
    }

    public PageResult(List<T> records, long total, long current, long size) {
        this.records = records == null ? new ArrayList<>() : records;
        this.total = total;
        this.current = current;
        this.size = size;
        this.pages = size <= 0 ? 0 : (total + size - 1) / size;
    }

    /** 由 MyBatis-Plus 的分页对象转换而来 */
    public static <T> PageResult<T> of(IPage<T> page) {
        return new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    /** 空分页（无数据场景） */
    public static <T> PageResult<T> empty(long current, long size) {
        return new PageResult<>(new ArrayList<>(), 0L, current, size);
    }

    // ==================== Getter / Setter ====================

    public List<T> getRecords() {
        return records;
    }

    public void setRecords(List<T> records) {
        this.records = records;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public long getCurrent() {
        return current;
    }

    public void setCurrent(long current) {
        this.current = current;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public long getPages() {
        return pages;
    }

    public void setPages(long pages) {
        this.pages = pages;
    }
}
