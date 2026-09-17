package com.online.study.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.online.study.entity.Homework;
import com.online.study.entity.HomeworkSubmit;
import com.online.study.mapper.HomeworkMapper;
import com.online.study.service.HomeworkService;
import com.online.study.service.HomeworkSubmitService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HomeworkServiceImpl extends ServiceImpl<HomeworkMapper, Homework> implements HomeworkService {

    @Autowired
    private HomeworkSubmitService homeworkSubmitService;

    /**
     * 级联删除作业：先删它的提交记录，再删作业本身。
     *
     * <p>两步必须在一个事务里。否则一旦「删完提交记录、删作业时失败」，
     * 就会出现「作业还在，但学生的提交记录已经没了」—— 而且无法恢复。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeHomeworkCascade(Integer homeworkId) {
        if (homeworkId == null) {
            return false;
        }
        homeworkSubmitService.remove(
                new QueryWrapper<HomeworkSubmit>().eq("homework_id", homeworkId));
        return removeById(homeworkId);
    }
}
