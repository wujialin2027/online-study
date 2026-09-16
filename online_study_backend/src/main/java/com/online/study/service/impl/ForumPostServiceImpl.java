package com.online.study.service.impl;

import com.online.study.entity.ForumPost;
import com.online.study.mapper.ForumPostMapper;
import com.online.study.service.ForumPostService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class  ForumPostServiceImpl extends ServiceImpl<ForumPostMapper, ForumPost> implements ForumPostService {
}
