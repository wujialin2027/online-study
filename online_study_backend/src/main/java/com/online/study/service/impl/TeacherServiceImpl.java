package com.online.study.service.impl;

import com.online.study.entity.Teacher;
import com.online.study.mapper.TeacherMapper;
import com.online.study.service.TeacherService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class TeacherServiceImpl extends ServiceImpl<TeacherMapper, Teacher> implements TeacherService {
}
