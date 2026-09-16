package com.online.study.service.impl;

import com.online.study.entity.Course;
import com.online.study.mapper.CourseMapper;
import com.online.study.service.CourseService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class CourseServiceImpl extends ServiceImpl<CourseMapper, Course> implements CourseService {
}
