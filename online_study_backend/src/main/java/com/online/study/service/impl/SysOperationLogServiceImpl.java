package com.online.study.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.online.study.entity.SysOperationLog;
import com.online.study.mapper.SysOperationLogMapper;
import com.online.study.service.SysOperationLogService;
import org.springframework.stereotype.Service;

@Service
public class SysOperationLogServiceImpl extends ServiceImpl<SysOperationLogMapper, SysOperationLog>
        implements SysOperationLogService {
}
