package com.online.study.service.impl;

import com.online.study.entity.Homework;
import com.online.study.mapper.HomeworkMapper;
import com.online.study.service.HomeworkService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class HomeworkServiceImpl extends ServiceImpl<HomeworkMapper, Homework> implements HomeworkService {
}
