package com.online.study.service.impl;

import com.online.study.entity.CourseResource;
import com.online.study.mapper.CourseResourceMapper;
import com.online.study.service.CourseResourceService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class CourseResourceServiceImpl extends ServiceImpl<CourseResourceMapper, CourseResource> implements CourseResourceService {
}
