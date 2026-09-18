package com.online.study.service;

import com.online.study.entity.HomeworkSubmit;
import com.baomidou.mybatisplus.extension.service.IService;
import com.online.study.vo.HomeworkSubmitStatsVO;

public interface HomeworkSubmitService extends IService<HomeworkSubmit> {

    /**
     * 统计某门课程下每份作业的提交与批改情况（教师端作业列表用）。
     *
     * <p>只需一次调用就能拿到「应交人数 + 每份作业的已交 / 已批改 / 待批改」，
     * 避免前端为了列表上的一个数字去逐份作业发请求。
     *
     * @param courseId 课程 ID
     * @return 统计结果；课程下还没有作业时 {@code items} 为空列表
     */
    HomeworkSubmitStatsVO courseStats(Integer courseId);
}
