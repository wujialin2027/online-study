package com.online.study.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.online.study.common.PageQuery;
import com.online.study.common.PageResult;
import com.online.study.common.Result;
import com.online.study.entity.SysOperationLog;
import com.online.study.service.SysOperationLogService;
import com.online.study.utils.QueryUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 操作日志查询接口
 *
 * <p>日志由 {@link com.online.study.aspect.OperationLogAspect} 自动写入，
 * 本接口<b>只提供查询</b>，限管理员访问 ——
 * 日志里含操作人、IP、请求参数，属于审计信息，不能对外开放。
 *
 * <p>请求体示例：
 * <pre>
 *   {"pageNum": 1, "pageSize": 20}                        // 全部日志
 *   {"pageNum": 1, "pageSize": 20, "module": "课程"}       // 只看课程模块
 *   {"pageNum": 1, "pageSize": 20, "operatorRole": "teacher"}
 *   {"pageNum": 1, "pageSize": 20, "success": 0}          // 只看失败的操作
 * </pre>
 * 查询条件经 {@code QueryUtil} 白名单校验，非法字段会被忽略。
 */
@RestController
@RequestMapping("/operation-log")
public class OperationLogController {

    @Autowired
    private SysOperationLogService service;

    /**
     * 分页查询操作日志，按操作时间倒序（最新的在最前）。
     */
    @PostMapping("/page")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<PageResult<SysOperationLog>> page(@RequestBody Map<String, Object> params) {
        Page<SysOperationLog> page = PageQuery.of(params);
        QueryWrapper<SysOperationLog> wrapper =
                QueryUtil.buildSafeWrapper(SysOperationLog.class, params);
        // 审计场景下"最近发生了什么"是主要诉求，固定按时间倒序
        wrapper.orderByDesc("create_time");
        return Result.success(PageResult.of(service.page(page, wrapper)));
    }
}
