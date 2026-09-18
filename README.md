# 在线学习平台（Online Study Platform）

> 面向 **学员 / 教师 / 管理员** 三种角色的在线学习平台，前后端分离。
> 后端 Spring Boot 3.3.5 + MyBatis-Plus + MySQL 8，前端 Vue 3 + Vite 5 + Element Plus，
> 内置**自研 RAG 智能助教**（不依赖任何 AI 框架，手工实现检索增强生成与 Function Calling 全链路）。

> 本项目早期为课程设计水平，当前处于持续改造升级中：升级 Spring Boot 3 / Java 21，
> 重构安全、成绩、论坛等模块，并新增 AI 智能助教。

---

## 在线演示

🔗 **http://182.92.199.30**

| 角色 | 账号 | 密码 | 演示数据 |
|---|---|---|---|
| 学员 | `student`（颜回） | `demo123` | 已选课、交作业、论坛发帖与收藏 |
| 教师 | `teacher`（孔子） | `demo123` | 3 门课程、课件、待批改作业 |
| 管理员 | `admin` | `123456` | 审批中心有待审申请 |

> 演示环境已**关闭自助注册**（防陌生人灌数据），请使用上方账号登录。
> 使用智能助教前，需在页面内点一次**重建知识库** —— 把课程/作业/资源数据切块、
> 向量化载入内存向量库（后端重启后需重新执行）。

除上表外，初始化脚本（`init.sql` 与 `sql/demo_data*.sql`）还生成了以下常规账号，密码统一 `123456`：

| 角色 | 账号 |
|---|---|
| 教师 | `teacher1` ~ `teacher5` |
| 学员 | `student1`、`student2`、`student4` ~ `student10` |

> 学号 `student3` 为空号（初始化脚本跳号），不是数据缺失。

---

## 功能总览

| 模块 | 说明 |
|---|---|
| 角色与权限 | 学员 / 教师 / 管理员三种角色，JWT 无状态认证，接口级鉴权 |
| 课程 | 教师发布课程、管理员审核、学员报名（人数上限、审核状态流转） |
| 课程资源 | 教师上传课件；学员**鉴权下载与在线预览**（PDF/图片/视频内嵌，防路径穿越） |
| 作业 | 教师按课程发布作业、在线批改评分；**列表直接显示每份作业的「已交 x/y」与待批改份数**；学员提交、查看成绩与评语 |
| 成绩 | 分数落在每次提交记录上，按课程聚合展示（已批改次数/平均分/待批改），未批改不计入平均 |
| 审批流 | 教师**删除课程 / 驳回报名**需提交申请（理由必填），管理员在「审批中心」同意后才执行，全程留痕；驳回申请待审批期间，报名显示「驳回审批中」 |
| 论坛 | 发帖/回复/点赞/收藏、限时编辑（30 分钟，服务端判定）、我的收藏/我的发帖、独立发帖页 |
| 智能助教 | RAG 知识库问答 + Function Calling 查询业务数据，按角色裁剪答案与工具 |
| 系统 | 操作日志注解、全局异常处理、统一返回结构 |

---

## 技术栈

| 层次 | 技术 |
|---|---|
| **后端** | Java 21、Spring Boot 3.3.5、Spring Security 6、MyBatis-Plus 3.5.7、JJWT 0.12 |
| **数据库** | MySQL 8 |
| **前端** | Vue 3（组合式 API）、Vite 5、Element Plus、Axios、ECharts 5 |
| **AI** | 百炼 OpenAI 兼容端点（对话 deepseek-v4.1-flash / 向量 text-embedding-v4）、自研内存向量库 |

---

## 快速开始

### 1. 环境要求

```
JDK 21、Maven 3.8+、Node.js 18+、MySQL 8.0
可选：百炼平台 API Key（使用智能助教时需要）
```

### 2. 初始化数据库

按顺序执行（顺序不能乱）：

```bash
mysql -uroot -p < init.sql                    # 建库建表
mysql -uroot -p < sql/migrate_v1.1.sql        # 增量迁移 v1.1 ~ v1.7
mysql -uroot -p < sql/migrate_v1.2.sql
# ... 依次到 migrate_v1.7.sql
mysql -uroot -p < sql/demo_data_v2.sql        # 演示数据
```

> ⚠️ **注意**：`init.sql` 第 1 条语句是 `DROP DATABASE IF EXISTS online_study`，
> 会先删除**名为 online_study 的库**再重建。仅适用于全新初始化；库中已有数据时**不要执行**。

### 3. 启动后端

```bash
cd online_study_backend

# 复制配置模板，然后按需修改（尤其是数据库账号密码）
cp src/main/resources/application.yml.example src/main/resources/application.yml

mvn spring-boot:run        # 默认端口 8081
```

> 真实配置文件 `application.yml` 已被 `.gitignore` 排除，不会进入仓库；
> 数据库密码等敏感信息建议通过环境变量注入（见模板中的 `${DB_PASSWORD}` 写法）。
> AI 相关配置见模板中的 `ai.*` 段（endpoint / api-key / 模型名）。

### 4. 启动前端

```bash
cd online_study_frontend
npm install
npm run dev                # 默认 http://localhost:3000，Vite 把 /api 代理到 8081
```

---

## 目录结构

```
online_study/
├── online_study_backend/          # 后端 Spring Boot 工程
│   └── src/main/java/com/online/study/
│       ├── ai/                    # 智能助教（RAG + 工具调用，8 个类）
│       ├── common/                # 统一返回 Result / 分页 PageResult / 状态码
│       ├── exception/             # 业务异常 + 全局异常处理
│       ├── config/                # Security / JWT 过滤器 / MyBatis-Plus / Web 配置
│       ├── controller/            # 接口层
│       ├── service/               # 业务层
│       ├── mapper/                # 数据访问层
│       ├── entity/ vo/            # 实体与视图对象
│       └── utils/                 # JWT、统一返回等工具
├── online_study_frontend/         # 前端 Vue3 工程
│   └── src/{views, router, utils, styles}
├── docs/                          # 需求与设计文档
├── sql/                           # 增量迁移与演示数据
└── init.sql                       # 建库建表脚本
```

---

## 接口约定

所有接口统一返回如下结构，前端只需在 Axios 拦截器一处解包：

```json
{ "code": 200, "message": "操作成功", "data": {} }
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
| [AI 智能助教模块设计](docs/AI模块设计.md) | RAG 与工具调用的设计决策与实现 |

---

## 开发计划（Roadmap）

- [x] 框架升级：Spring Boot 2.7.15 → 3.3.5，适配 Spring Security 6 与 JJWT 0.12
- [x] 工程底座：统一返回、全局异常、参数校验、分页插件
- [x] 安全加固：接口级鉴权、身份可信化、报名审核越权修复
- [x] 业务增强：报名名额控制、课程审核工作流、操作日志
- [x] 成绩模型重构：分数落提交记录、课程两级展示、未批改不计平均
- [x] 论坛改造：限时编辑、收藏入口、独立发帖页、作者姓名展示
- [x] 资源鉴权下载与在线预览（防路径穿越、RFC 5987 中文文件名）
- [x] 智能助教：自研 RAG 问答 + Function Calling（按角色裁剪）
- [ ] 向量库持久化（替换内存实现，解决重启丢失）
- [ ] 混合检索（向量 + 关键词）与重排序
- [ ] 回答引用溯源（标注答案来自哪份资料）
- [x] 部署上线：Nginx 反向代理 + 云服务器（在线演示：<http://182.92.199.30>）

---

## 许可证

待补充（拟采用 MIT License）。
