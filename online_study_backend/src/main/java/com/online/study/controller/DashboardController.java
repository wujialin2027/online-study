package com.online.study.controller;

import com.online.study.common.Result;
import com.online.study.service.DashboardService;
import com.online.study.vo.DashboardVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 首页数据概览接口
 *
 * <p>同一个接口返回三种角色各自的数据 —— 具体返回哪套由服务端根据 JWT 里的角色决定，
 * 前端不需要（也无法）指定。这样学员登录不会拿到全站运营数据。
 */
@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    @Autowired
    private DashboardService dashboardService;

    /**
     * 获取当前登录用户的首页概览数据（数字卡片 + 图表）。
     */
    @GetMapping("/overview")
    public Result<DashboardVO> overview() {
        return Result.success(dashboardService.overview());
    }
}
