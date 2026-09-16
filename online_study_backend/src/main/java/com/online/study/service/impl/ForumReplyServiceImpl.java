package com.online.study.service.impl;

import com.online.study.entity.ForumReply;
import com.online.study.mapper.ForumReplyMapper;
import com.online.study.service.ForumReplyService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class ForumReplyServiceImpl extends ServiceImpl<ForumReplyMapper, ForumReply> implements ForumReplyService {
}
