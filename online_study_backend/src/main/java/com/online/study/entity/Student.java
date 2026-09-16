package com.online.study.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import lombok.Data;
import java.util.Date;

@Data
@TableName("student")
public class Student {
    @TableId(type = IdType.AUTO)
    private Integer studentId;
    private String studentAccount;
    private String studentPwd;
    private String studentName;
    private String studentPhone;
    private String studentEmail;
    private Date registerTime;
    private Integer accountStatus;
}
