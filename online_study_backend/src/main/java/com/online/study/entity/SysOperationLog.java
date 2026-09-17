package com.online.study.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 系统操作日志
 *
 * <p>对应表 {@code sys_operation_log}（在 migrate_v1.1.sql 中创建）。
 * 数据只由 {@link com.online.study.aspect.OperationLogAspect} 写入，
 * 对外只提供查询接口，因此没有给前端开放任何写入入口。
 */
@Data
@TableName("sys_operation_log")
public class SysOperationLog {

    @TableId(type = IdType.AUTO)
    private Long logId;

    /** 操作人 ID（来自 JWT 解析结果，不是前端传值） */
    private Integer operatorId;

    /** 操作人账号 */
    private String operatorName;

    /** 操作人角色：student / teacher / admin */
    private String operatorRole;

    /** 业务模块，如「课程」「报名」 */
    private String module;

    /** 操作描述，如「审核课程」 */
    private String operation;

    /** 请求地址 */
    private String requestUri;

    /** 请求方法 GET / POST / PUT / DELETE */
    private String requestMethod;

    /** 请求参数（已对 password 等字段脱敏） */
    private String requestParams;

    /** 客户端 IP */
    private String ip;

    /** 是否成功：1 成功 / 0 失败（用 Integer 而非 Boolean，避免 tinyint 映射歧义） */
    private Integer success;

    /** 失败原因（异常信息，截断到 500 字符内） */
    private String errorMsg;

    /** 接口耗时（毫秒） */
    private Long costMs;

    /** 操作时间 */
    private Date createTime;
}
