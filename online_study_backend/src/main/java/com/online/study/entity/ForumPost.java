package com.online.study.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import lombok.Data;
import java.util.Date;

@Data
@TableName("forum_post")
public class ForumPost {
    @TableId(type = IdType.AUTO)
    private Integer postId;
    private Integer publisherId;
    private String publisherRole;
    private String postTitle;
    private String postContent;
    private Date publishTime;
    /** 最后一次编辑时间（null = 从未编辑过，前端据此显示「已编辑」角标） */
    private Date editTime;
    private Integer likeNum;
    private Integer collectNum;
}

