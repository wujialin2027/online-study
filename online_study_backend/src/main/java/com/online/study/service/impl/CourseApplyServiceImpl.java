package com.online.study.service.impl;

import com.online.study.entity.CourseApply;
import com.online.study.mapper.CourseApplyMapper;
import com.online.study.service.CourseApplyService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class CourseApplyServiceImpl extends ServiceImpl<CourseApplyMapper, CourseApply> implements CourseApplyService {
}
