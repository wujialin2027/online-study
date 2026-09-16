package com.online.study.service.impl;

import com.online.study.entity.Score;
import com.online.study.mapper.ScoreMapper;
import com.online.study.service.ScoreService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class ScoreServiceImpl extends ServiceImpl<ScoreMapper, Score> implements ScoreService {
}
