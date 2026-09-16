# 在线学习平台（Online Study Platform）

> 面向 **学员 / 教师 / 管理员** 三种角色的在线学习平台，前后端分离。
> 后端 Spring Boot 3.3.5 + MyBatis-Plus + MySQL 8，前端 Vue 3 + Vite 5 + Element Plus。

---

## 项目简介

平台围绕「课程」这一核心业务展开，覆盖 **课程管理 → 课程资源 → 报名 → 作业提交与批改 → 成绩 → 论坛交流** 的完整教学闭环，并提供数据概览看板。

| 角色 | 能做什么 |
|---|---|
| **学员** | 浏览与报名课程、下载课程资源、提交作业、查看成绩、参与论坛 |
| **教师** | 发布与维护课程、上传课程资源、发布作业、批改作业与录入成绩、参与论坛 |
| **管理员** | 用户管理、课程审核、全局数据概览 |

---

## 技术栈

| 层次 | 技术 |
|---|---|
| **后端** | Java 21、Spring Boot 3.3.5、Spring Security 6、MyBatis-Plus 3.5.7、JJWT 0.12 |
| **数据库** | MySQL 8 |
| **前端** | Vue 3、Vite 5、Element Plus、Axios、ECharts |

---

## 快速开始

### 1. 环境要求

```
JDK 21
Maven 3.8+
Node.js 18+
MySQL 8.0
```

### 2. 初始化数据库

```bash
mysql -uroot -p < init.sql
```

> ⚠️ **注意**：`init.sql` 第 1 条语句是 `DROP DATABASE`，会**先删除同名数据库再重建**。
> 仅适用于全新初始化；库中已有数据时**不要执行**，否则数据会全部丢失。

### 3. 启动后端

```bash
cd online_study_backend

# 复制配置模板，然后按需修改（尤其是数据库账号密码）
cp src/main/resources/application.yml.example src/main/resources/application.yml

# 启动
mvn spring-boot:run
```

后端默认运行在 **http://localhost:8081**。

> 真实配置文件 `application.yml` 已被 `.gitignore` 排除，不会进入仓库。
> 数据库密码等敏感信息建议通过环境变量注入（见模板中的 `${DB_PASSWORD}` 写法）。

### 4. 启动前端

```bash
cd online_study_frontend
npm install
npm run dev
```

前端默认运行在 **http://localhost:3000**（Vite 会把 `/api` 代理到后端 8081）。

---

## 目录结构

```
online_study/
├── online_study_backend/          # 后端 Spring Boot 工程
│   └── src/main/java/com/online/study/
│       ├── common/                # 统一返回 Result / 分页 PageResult / 状态码
│       ├── exception/             # 业务异常 + 全局异常处理
│       ├── config/                # Security / JWT 过滤器 / MyBatis-Plus / Web 配置
│       ├── controller/            # 接口层
│       ├── service/               # 业务层
│       ├── mapper/                # 数据访问层
│       ├── entity/                # 实体
│       └── utils/                 # 工具类
├── online_study_frontend/         # 前端 Vue3 工程
│   └── src/
│       ├── views/                 # 页面
│       ├── router/                # 路由
│       └── utils/                 # Axios 封装
├── docs/                          # 需求与设计文档
└── init.sql                       # 建库建表脚本
```

---

## 接口约定

所有接口统一返回如下结构，前端只需在 Axios 拦截器一处解包：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {}
}
```

| 层 | 处理方式 |
|---|---|
| **安全层**（未登录 / 无权限） | 返回 HTTP **401 / 403**，前端据此跳转登录页 |
| **业务层**（参数错误 / 业务规则不满足） | HTTP 状态码恒为 **200**，真实结果看响应体 `code` 字段 |

分页接口额外返回 `records / total / current / size / pages`。

---

## 项目文档

| 文档 | 说明 |
|---|---|
| [需求分析](docs/需求分析.md) | 业务需求梳理 |
| [详细需求列表](docs/详细需求列表.md) | 功能点明细 |
| [表设计](docs/表设计.md) | 数据库表结构与字段说明 |
| [项目实现与结构](docs/项目实现与结构.md) | 代码结构说明 |

---

## 开发计划（Roadmap）

- [x] 框架升级：Spring Boot 2.7.15 → 3.3.5，适配 Spring Security 6 与 JJWT 0.12
- [x] 工程底座：统一返回、全局异常、参数校验、分页插件
- [ ] 安全加固：接口级鉴权、身份可信化、输入校验、上传白名单
- [ ] 业务增强：报名名额控制、课程审核工作流、操作日志
- [ ] 智能助教：基于课程与论坛内容的检索增强问答（RAG）
- [ ] 部署上线：Nginx 反向代理 + 云服务器

---

## 许可证

待补充（拟采用 MIT License）。
