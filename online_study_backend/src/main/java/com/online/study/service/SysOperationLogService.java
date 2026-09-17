package com.online.study.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.online.study.entity.SysOperationLog;

/**
 * 操作日志服务
 *
 * <p>写入由切面直接调用 {@code save}（继承自 IService），
 * 这里不额外定义业务方法，保持简单。
 */
public interface SysOperationLogService extends IService<SysOperationLog> {
}
