package com.online.study.service;

import com.online.study.entity.Homework;
import com.baomidou.mybatisplus.extension.service.IService;

public interface HomeworkService extends IService<Homework> {

    /**
     * 级联删除作业及其提交记录。
     *
     * <p>放在 Service 层是为了加 {@code @Transactional}：
     * 「删提交记录」和「删作业」必须是一个原子操作。
     *
     * @param homeworkId 作业 ID
     * @return 是否删除成功
     */
    boolean removeHomeworkCascade(Integer homeworkId);
}
