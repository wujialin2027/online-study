package com.online.study.service;

import com.online.study.vo.DashboardVO;

/**
 * 首页数据概览服务
 */
public interface DashboardService {

    /**
     * 按当前登录角色返回首页数据。
     *
     * <p>角色来自 JWT 解析结果，不由前端指定 ——
     * 否则学员只要传个 role=admin 就能看到全站运营数据。
     *
     * @return 管理员 / 教师 / 学员三套不同视角的概览数据
     */
    DashboardVO overview();
}
