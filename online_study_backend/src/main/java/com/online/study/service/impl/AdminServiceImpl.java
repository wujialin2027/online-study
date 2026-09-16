package com.online.study.service.impl;

import com.online.study.entity.Admin;
import com.online.study.mapper.AdminMapper;
import com.online.study.service.AdminService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class AdminServiceImpl extends ServiceImpl<AdminMapper, Admin> implements AdminService {
}
