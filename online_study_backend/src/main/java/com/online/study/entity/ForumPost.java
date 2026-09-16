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
    private Integer likeNum;
    private Integer collectNum;
}

