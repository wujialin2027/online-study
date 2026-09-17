package com.online.study.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.online.study.entity.SysOperationLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 操作日志 Mapper（只用到 MyBatis-Plus 的基础 CRUD）
 */
@Mapper
public interface SysOperationLogMapper extends BaseMapper<SysOperationLog> {
}
