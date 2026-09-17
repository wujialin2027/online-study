package com.online.study.common;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.Map;

/**
 * 分页参数解析工具
 *
 * <p>前端统一约定：请求体里带 {@code pageNum} 和 {@code pageSize} 两个字段，例如：
 * <pre>
 * { "pageNum": 2, "pageSize": 10, "courseName": "Java" }
 * </pre>
 *
 * <p>这里做三件保护：
 * <ol>
 *   <li><b>默认值</b>：不传就按第 1 页、每页 10 条，不让前端因为漏传而报错</li>
 *   <li><b>非法值回退</b>：传了 {@code "abc"} 这种非数字，回退默认值而不是抛异常</li>
 *   <li><b>上限保护</b>：{@code pageSize} 最大 500。否则有人传 {@code pageSize=999999}
 *       就等于一次把整张表拉出来 —— 分页的意义直接归零</li>
 * </ol>
 */
public final class PageQuery {

    public static final long DEFAULT_PAGE_NUM = 1L;
    public static final long DEFAULT_PAGE_SIZE = 10L;
    /** 单页上限，与 {@code MybatisPlusConfig} 里的 maxLimit 保持一致 */
    public static final long MAX_PAGE_SIZE = 500L;

    private PageQuery() {
        // 工具类不允许实例化
    }

    /** 从请求参数里解析分页对象；参数缺失或非法时回退到默认值 */
    public static <T> Page<T> of(Map<String, Object> params) {
        long pageNum = parseLong(params, "pageNum", DEFAULT_PAGE_NUM);
        long pageSize = parseLong(params, "pageSize", DEFAULT_PAGE_SIZE);

        if (pageNum < 1) {
            pageNum = DEFAULT_PAGE_NUM;
        }
        if (pageSize < 1) {
            pageSize = DEFAULT_PAGE_SIZE;
        }
        if (pageSize > MAX_PAGE_SIZE) {
            pageSize = MAX_PAGE_SIZE;
        }

        return new Page<>(pageNum, pageSize);
    }

    private static long parseLong(Map<String, Object> params, String key, long defaultValue) {
        Object value = params == null ? null : params.get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.toString().trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
