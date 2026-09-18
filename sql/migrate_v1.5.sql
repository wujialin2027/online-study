-- ============================================================================
-- v1.5 演示课件资源：把 example.com 假地址换成真实文件
-- ----------------------------------------------------------------------------
-- 背景（这是一个"看起来像 bug 的假数据"）：
--   原来 course_resource 里三条演示数据写的是 http://example.com/java_ch1.pdf，
--   点「下载」会跳到 example.com 的 "Example Domain" 页面 —— 因为 example.com
--   是国际标准示例域名，任何路径都返回同一张占位页。
--   代码没问题（前端只是跳到该地址），是数据本身不可用。
--
-- 处理方式：
--   改为指向 online_study_backend/uploads/ 下真实存在的三个演示 PDF
--   （由 _local/gen_demo_resources.py 生成，文件名以 demo- 开头，
--     .gitignore 里有 `!**/uploads/demo-*` 例外规则，会随仓库一起入库）。
--
-- ⚠️ 注意：uploads 目录的位置由 file.upload.path 决定（默认 uploads/，
--    相对于后端的启动工作目录）。从 IDEA 启动时工作目录是 online_study_backend/，
--    所以文件要放在 online_study_backend/uploads/ 下。
-- ============================================================================

UPDATE course_resource SET resource_path = '/uploads/demo-java-ch1.pdf'
WHERE resource_id = 1;

-- 原来这条是「Java环境搭建视频 / Video」，但仓库里没有真实视频文件，
-- 保留 Video 类型会指向一个不存在的 .mp4 —— 改成同一主题的讲义（PDF）。
UPDATE course_resource
SET resource_name = 'Java环境搭建讲义',
    resource_type = 'PDF',
    resource_path = '/uploads/demo-java-env.pdf'
WHERE resource_id = 2;

UPDATE course_resource SET resource_path = '/uploads/demo-springboot-src.pdf'
WHERE resource_id = 3;

-- 校验：resource_path 不应再出现 example.com
SELECT resource_id, course_id, resource_name, resource_type, resource_path
FROM course_resource
ORDER BY course_id, resource_id;
