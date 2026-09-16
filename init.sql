-- ============================================================
-- ⚠️ 执行前必读
-- 第 1 条语句是 DROP DATABASE —— 会先【删除同名数据库】再重建。
-- 仅在全新初始化时执行；库中已有数据时执行本脚本将导致数据全部丢失。
-- 已有数据时请改用增量迁移脚本，不要直接跑本文件。
-- ============================================================
DROP DATABASE IF EXISTS online_study;
CREATE DATABASE IF NOT EXISTS online_study DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE online_study;

-- 表2.1 学员表（student）
CREATE TABLE IF NOT EXISTS student (
    student_id INT AUTO_INCREMENT PRIMARY KEY COMMENT '学员唯一标识ID',
    student_account VARCHAR(50) NOT NULL COMMENT '学员登录账号',
    student_pwd VARCHAR(100) NOT NULL COMMENT '学员登录密码（加密存储）',
    student_name VARCHAR(20) NOT NULL COMMENT '学员姓名',
    student_phone VARCHAR(11) NOT NULL COMMENT '学员手机号',
    student_email VARCHAR(50) COMMENT '学员电子邮箱',
    register_time DATETIME NOT NULL COMMENT '学员注册时间',
    account_status INT(1) NOT NULL DEFAULT 1 COMMENT '账号状态（0-禁用，1-正常）'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学员表';

-- 表2.2 教师表（teacher）
CREATE TABLE IF NOT EXISTS teacher (
    teacher_id INT AUTO_INCREMENT PRIMARY KEY COMMENT '教师唯一标识ID',
    teacher_account VARCHAR(50) NOT NULL COMMENT '教师登录账号',
    teacher_pwd VARCHAR(100) NOT NULL COMMENT '教师登录密码（加密存储）',
    teacher_name VARCHAR(20) NOT NULL COMMENT '教师姓名',
    teacher_phone VARCHAR(11) NOT NULL COMMENT '教师手机号',
    teacher_email VARCHAR(50) COMMENT '教师电子邮箱',
    teacher_org VARCHAR(50) NOT NULL COMMENT '教师所属机构',
    register_time DATETIME NOT NULL COMMENT '教师注册时间',
    account_status INT(1) NOT NULL DEFAULT 1 COMMENT '账号状态（0-禁用，1-正常）'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='教师表';

-- 表2.3 管理员表（admin）
CREATE TABLE IF NOT EXISTS admin (
    admin_id INT AUTO_INCREMENT PRIMARY KEY COMMENT '管理员唯一标识ID',
    admin_account VARCHAR(50) NOT NULL COMMENT '管理员登录账号',
    admin_pwd VARCHAR(100) NOT NULL COMMENT '管理员登录密码（加密存储）',
    admin_name VARCHAR(20) NOT NULL COMMENT '管理员姓名',
    admin_phone VARCHAR(11) COMMENT '管理员手机号',
    role_level INT(1) NOT NULL DEFAULT 0 COMMENT '角色等级（0-普通管理员，1-超级管理员）'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员表';

-- 表2.4 课程表（course）
CREATE TABLE IF NOT EXISTS course (
    course_id INT AUTO_INCREMENT PRIMARY KEY COMMENT '课程唯一标识ID',
    course_name VARCHAR(50) NOT NULL COMMENT '课程名称',
    course_intro TEXT COMMENT '课程介绍',
    train_cycle VARCHAR(30) NOT NULL COMMENT '培训周期',
    apply_cond VARCHAR(100) COMMENT '课程报名条件',
    publish_teacher_id INT NOT NULL COMMENT '发布课程教师ID',
    audit_status INT(1) NOT NULL DEFAULT 0 COMMENT '审核状态（0-未审核，1-审核通过，2-审核驳回）',
    publish_time DATETIME NOT NULL COMMENT '课程发布时间',
    offline_time DATETIME COMMENT '课程下架时间',
    CONSTRAINT fk_course_teacher FOREIGN KEY (publish_teacher_id) REFERENCES teacher(teacher_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程表';

-- 表2.5 课程资源表（course_resource）
CREATE TABLE IF NOT EXISTS course_resource (
    resource_id INT AUTO_INCREMENT PRIMARY KEY COMMENT '资源唯一标识ID',
    course_id INT NOT NULL COMMENT '所属课程ID',
    resource_name VARCHAR(50) NOT NULL COMMENT '资源名称',
    resource_type VARCHAR(20) NOT NULL COMMENT '资源类型（视频/课件/资料）',
    resource_path VARCHAR(200) NOT NULL COMMENT '资源存储路径',
    resource_photo VARCHAR(255) COMMENT '资源照片',
    upload_time DATETIME NOT NULL COMMENT '资源上传时间',
    upload_teacher_id INT NOT NULL COMMENT '上传资源教师ID',
    CONSTRAINT fk_resource_course FOREIGN KEY (course_id) REFERENCES course(course_id),
    CONSTRAINT fk_resource_teacher FOREIGN KEY (upload_teacher_id) REFERENCES teacher(teacher_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程资源表';

-- 表2.6 作业表（homework）
CREATE TABLE IF NOT EXISTS homework (
    homework_id INT AUTO_INCREMENT PRIMARY KEY COMMENT '作业唯一标识ID',
    course_id INT NOT NULL COMMENT '所属课程ID',
    homework_name VARCHAR(50) NOT NULL COMMENT '作业名称',
    homework_content TEXT NOT NULL COMMENT '作业内容',
    deadline DATETIME NOT NULL COMMENT '作业提交截止时间',
    publish_teacher_id INT NOT NULL COMMENT '发布作业教师ID',
    publish_time DATETIME NOT NULL COMMENT '作业发布时间',
    CONSTRAINT fk_homework_course FOREIGN KEY (course_id) REFERENCES course(course_id),
    CONSTRAINT fk_homework_teacher FOREIGN KEY (publish_teacher_id) REFERENCES teacher(teacher_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='作业表';

-- 表2.7 课程报名表（course_apply）
CREATE TABLE IF NOT EXISTS course_apply (
    apply_id INT AUTO_INCREMENT PRIMARY KEY COMMENT '报名唯一标识ID',
    student_id INT NOT NULL COMMENT '报名学员ID',
    course_id INT NOT NULL COMMENT '报名课程ID',
    apply_time DATETIME NOT NULL COMMENT '报名时间',
    audit_status INT(1) NOT NULL DEFAULT 0 COMMENT '审核状态（0-未审核，1-审核通过，2-审核驳回）',
    audit_teacher_id INT COMMENT '审核教师ID',
    CONSTRAINT fk_apply_student FOREIGN KEY (student_id) REFERENCES student(student_id),
    CONSTRAINT fk_apply_course FOREIGN KEY (course_id) REFERENCES course(course_id),
    CONSTRAINT fk_apply_teacher FOREIGN KEY (audit_teacher_id) REFERENCES teacher(teacher_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程报名表';

-- 表2.8 作业提交表（homework_submit）
CREATE TABLE IF NOT EXISTS homework_submit (
    submit_id INT AUTO_INCREMENT PRIMARY KEY COMMENT '提交唯一标识ID',
    student_id INT NOT NULL COMMENT '提交学员ID',
    homework_id INT NOT NULL COMMENT '提交作业ID',
    submit_content TEXT NOT NULL COMMENT '作业提交内容',
    submit_file VARCHAR(255) COMMENT '提交的附件/照片路径',
    submit_photo VARCHAR(255) COMMENT '提交的作业照片',
    submit_time DATETIME NOT NULL COMMENT '作业提交时间',
    correct_status INT(1) NOT NULL DEFAULT 0 COMMENT '批改状态（0-未批改，1-已批改）',
    CONSTRAINT fk_submit_student FOREIGN KEY (student_id) REFERENCES student(student_id),
    CONSTRAINT fk_submit_homework FOREIGN KEY (homework_id) REFERENCES homework(homework_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='作业提交表';

-- 表2.9 成绩表（score）
CREATE TABLE IF NOT EXISTS score (
    score_id INT AUTO_INCREMENT PRIMARY KEY COMMENT '成绩唯一标识ID',
    student_id INT NOT NULL COMMENT '学员ID',
    course_id INT NOT NULL COMMENT '课程ID',
    homework_score INT(3) COMMENT '作业成绩（0-100）',
    exam_score INT(3) COMMENT '考试成绩（0-100）',
    total_score INT(3) NOT NULL COMMENT '课程总成绩（0-100）',
    score_comment VARCHAR(500) COMMENT '教师评语',
    score_teacher_id INT NOT NULL COMMENT '评分教师ID',
    score_time DATETIME NOT NULL COMMENT '评分时间',
    CONSTRAINT fk_score_student FOREIGN KEY (student_id) REFERENCES student(student_id),
    CONSTRAINT fk_score_course FOREIGN KEY (course_id) REFERENCES course(course_id),
    CONSTRAINT fk_score_teacher FOREIGN KEY (score_teacher_id) REFERENCES teacher(teacher_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='成绩表';

-- 表2.10 论坛帖子表（forum_post）
CREATE TABLE IF NOT EXISTS forum_post (
    post_id INT AUTO_INCREMENT PRIMARY KEY COMMENT '帖子唯一标识ID',
    publisher_id INT NOT NULL COMMENT '发布者ID（学员/教师ID）',
    publisher_role VARCHAR(10) NOT NULL COMMENT '发布者角色（学员/教师）',
    post_title VARCHAR(100) NOT NULL COMMENT '帖子标题',
    post_content TEXT NOT NULL COMMENT '帖子内容',
    publish_time DATETIME NOT NULL COMMENT '帖子发布时间',
    like_num INT NOT NULL DEFAULT 0 COMMENT '帖子点赞数',
    collect_num INT NOT NULL DEFAULT 0 COMMENT '帖子收藏数'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='论坛帖子表';

-- 表2.11 论坛回复表（forum_reply）
CREATE TABLE IF NOT EXISTS forum_reply (
    reply_id INT AUTO_INCREMENT PRIMARY KEY COMMENT '回复唯一标识ID',
    post_id INT NOT NULL COMMENT '所属帖子ID',
    replier_id INT NOT NULL COMMENT '回复者ID（学员/教师ID）',
    replier_role VARCHAR(10) NOT NULL COMMENT '回复者角色（学员/教师）',
    reply_content TEXT NOT NULL COMMENT '回复内容',
    reply_time DATETIME NOT NULL COMMENT '回复时间',
    CONSTRAINT fk_reply_post FOREIGN KEY (post_id) REFERENCES forum_post(post_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='论坛回复表';

-- 初始化超级管理员
INSERT INTO admin (admin_account, admin_pwd, admin_name, admin_phone, role_level) 
VALUES ('admin', '$2a$10$Mamia1SxQ4A7EwtmexR94.VjRTH5049bQV5oX3h5A1Oi0tldl3/LO', '超级管理员', '13800138000', 1);
-- 密码明文是 123456 (BCrypt加密)
 
 INSERT INTO teacher (teacher_account, teacher_pwd, teacher_name, teacher_phone, teacher_email, teacher_org, register_time, account_status) VALUES
 ('teacher1', '$2a$10$Mamia1SxQ4A7EwtmexR94.VjRTH5049bQV5oX3h5A1Oi0tldl3/LO', '张老师', '13800000001', 'zhang@example.com', '计算机学院', NOW(), 1),
 ('teacher2', '$2a$10$Mamia1SxQ4A7EwtmexR94.VjRTH5049bQV5oX3h5A1Oi0tldl3/LO', '李老师', '13800000002', 'li@example.com', '软件工程学院', NOW(), 1);
 
 INSERT INTO student (student_account, student_pwd, student_name, student_phone, student_email, register_time, account_status) VALUES
 ('student1', '$2a$10$Mamia1SxQ4A7EwtmexR94.VjRTH5049bQV5oX3h5A1Oi0tldl3/LO', '王同学', '13900000001', 'wang@example.com', NOW(), 1),
 ('student2', '$2a$10$Mamia1SxQ4A7EwtmexR94.VjRTH5049bQV5oX3h5A1Oi0tldl3/LO', '赵同学', '13900000002', 'zhao@example.com', NOW(), 1);
 
 INSERT INTO course (course_name, course_intro, train_cycle, apply_cond, publish_teacher_id, audit_status, publish_time) VALUES
 ('Java基础教程', '从零开始学Java，包含面向对象基础。', '4周', '无限制', 1, 1, NOW()),
 ('Spring Boot高级实战', '深入理解Spring Boot原理及微服务开发。', '6周', '有Java基础', 1, 1, NOW()),
 ('Vue3全家桶实战', '构建现代化前端应用。', '5周', '有JS基础', 2, 0, NOW());
 
 INSERT INTO course_resource (course_id, resource_name, resource_type, resource_path, resource_photo, upload_time, upload_teacher_id) VALUES
(1, 'Java基础第一章课件', 'PDF', 'http://example.com/java_ch1.pdf', NULL, NOW(), 1),
(1, 'Java环境搭建视频', 'Video', 'http://example.com/java_env.mp4', NULL, NOW(), 1),
(2, 'Spring Boot源码分析', 'PDF', 'http://example.com/springboot_src.pdf', NULL, NOW(), 1);
 
 INSERT INTO homework (course_id, homework_name, homework_content, deadline, publish_teacher_id, publish_time) VALUES
 (1, 'Java基础第一次作业', '请完成课后练习题1-5。', DATE_ADD(NOW(), INTERVAL 7 DAY), 1, NOW()),
 (2, 'Spring Boot实战作业', '实现一个简单的RESTful API并完成CRUD。', DATE_ADD(NOW(), INTERVAL 7 DAY), 1, NOW());
 
 INSERT INTO course_apply (student_id, course_id, apply_time, audit_status, audit_teacher_id) VALUES
 (1, 1, NOW(), 1, 1),
 (2, 1, NOW(), 0, NULL),
 (1, 2, NOW(), 1, 1);
 
-- 插入测试作业提交数据
INSERT INTO homework_submit (student_id, homework_id, submit_content, submit_file, submit_photo, submit_time, correct_status) VALUES
(1, 1, '这是我的作业解答内容...', NULL, '/static/photos/homework1.jpg', NOW(), 1), -- 已批改
(1, 2, 'RESTful API实现代码如下...', NULL, NULL, NOW(), 0); -- 未批改

INSERT INTO score (student_id, course_id, homework_score, exam_score, total_score, score_teacher_id, score_time) VALUES
 (1, 1, 90, 85, 88, 1, NOW());
 
 INSERT INTO forum_post (publisher_id, publisher_role, post_title, post_content, publish_time, like_num, collect_num) VALUES
 (1, 'student', '关于Java环境配置的问题', '请问在Mac上配置Java环境变量的具体步骤是什么？', NOW(), 5, 2),
 (1, 'teacher', 'Spring Boot学习资料推荐', '推荐大家阅读官方文档和实战书籍。', NOW(), 10, 8);
 
 INSERT INTO forum_reply (post_id, replier_id, replier_role, reply_content, reply_time) VALUES
 (1, 1, 'teacher', '可以参考这个链接：...', NOW()),
 (1, 2, 'student', '我也遇到了这个问题，同求！', NOW());
