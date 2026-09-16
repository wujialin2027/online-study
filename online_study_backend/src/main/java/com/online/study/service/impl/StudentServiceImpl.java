package com.online.study.service.impl;

import com.online.study.entity.Student;
import com.online.study.mapper.StudentMapper;
import com.online.study.service.StudentService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class StudentServiceImpl extends ServiceImpl<StudentMapper, Student> implements StudentService {
}
