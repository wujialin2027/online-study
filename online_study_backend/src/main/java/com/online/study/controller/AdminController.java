package com.online.study.controller;

import com.online.study.entity.Admin;
import com.online.study.service.AdminService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.online.study.utils.QueryUtil;

@RestController
@RequestMapping("/admin")
public class AdminController {

    @Autowired
    private AdminService service;

    @GetMapping("/list")
    public List<Admin> list() {
        return service.list();
    }

    @PostMapping("/query")
    public List<Admin> query(@RequestBody Map<String, Object> params) {
        QueryWrapper<Admin> wrapper = QueryUtil.buildSafeWrapper(Admin.class, params);
        return service.list(wrapper);
    }

    @PostMapping("/save")
    public boolean save(@RequestBody Admin entity) {
        return service.saveOrUpdate(entity);
    }

    @DeleteMapping("/{id}")
    public boolean delete(@PathVariable Integer id) {
        return service.removeById(id);
    }
}
