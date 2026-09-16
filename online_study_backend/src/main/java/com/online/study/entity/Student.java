package com.online.study.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.Date;

@Data
@TableName("student")
public class Student {
    @TableId(type = IdType.AUTO)
    private Integer studentId;
    private String studentAccount;

    /**
     * 密码（BCrypt 哈希值）。
     *
     * <p>{@code WRITE_ONLY} 的含义是「只许写入、不许输出」：
     * 前端提交过来的 JSON 仍然能正常反序列化到这个字段（注册、改密码要用），
     * 但**任何接口把它序列化返回给前端时，这个字段都会被自动剔除**。
     *
     * <p>原代码没有这个注解，导致所有 {@code /student/list}、登录接口都把整张用户表
     * （含密码哈希、手机号）原样返回给前端 —— 属于必须修掉的安全缺陷。
     *
     * <p>为什么不用 {@code @JsonIgnore}：那个是「读写都屏蔽」，
     * 会导致前端传过来的 password 也收不到，注册功能会直接失效。
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String studentPwd;

    private String studentName;
    private String studentPhone;
    private String studentEmail;
    private Date registerTime;
    private Integer accountStatus;
}
