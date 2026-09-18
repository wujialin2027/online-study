package com.online.study.controller;

import com.online.study.entity.Course;
import com.online.study.entity.CourseApply;
import com.online.study.entity.CourseResource;
import com.online.study.exception.BizException;
import com.online.study.mapper.CourseApplyMapper;
import com.online.study.mapper.CourseMapper;
import com.online.study.service.CourseResourceService;
import com.online.study.common.ResultCode;
import com.online.study.utils.CurrentUserUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.online.study.utils.QueryUtil;
import com.online.study.common.PageQuery;
import com.online.study.common.PageResult;
import com.online.study.common.Result;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

@RestController
@RequestMapping("/course-resource")
public class CourseResourceController {

    /** 与 FileController 共用同一个上传目录配置，避免"存的地方"和"取的地方"不一致 */
    @Value("${file.upload.path:uploads/}")
    private String uploadPath;

    @Autowired
    private CourseResourceService service;

    @Autowired
    private CourseMapper courseMapper;

    @Autowired
    private CourseApplyMapper courseApplyMapper;

    @GetMapping("/list")
    public List<CourseResource> list() {
        return service.list();
    }

    @PostMapping("/query")
    public List<CourseResource> query(@RequestBody Map<String, Object> params) {
        QueryWrapper<CourseResource> wrapper = QueryUtil.buildSafeWrapper(CourseResource.class, params);
        return service.list(wrapper);
    }

    /**
     * 分页查询。
     * 请求体示例：{"pageNum": 1, "pageSize": 10, "courseName": "Java"}
     * 前两个字段由 PageQuery 解析（含默认值与上限保护），其余作为查询条件经白名单校验。
     */
    @PostMapping("/page")
    public Result<PageResult<CourseResource>> page(@RequestBody Map<String, Object> params) {
        Page<CourseResource> page = PageQuery.of(params);
        QueryWrapper<CourseResource> wrapper = QueryUtil.buildSafeWrapper(CourseResource.class, params);
        return Result.success(PageResult.of(service.page(page, wrapper)));
    }

    @PostMapping("/save")
    public boolean save(@RequestBody CourseResource entity) {
        if (!StringUtils.hasText(entity.getResourceName()) || !StringUtils.hasText(entity.getResourceType())) {
            throw new IllegalArgumentException("资源名称和资源类型不能为空");
        }
        if (!StringUtils.hasText(entity.getResourcePath())) {
            throw new IllegalArgumentException("请先上传资源文件");
        }
        return service.saveOrUpdate(entity);
    }

    @DeleteMapping("/{id}")
    public boolean delete(@PathVariable Integer id) {
        return service.removeById(id);
    }

    /**
     * 下载 / 预览课件（带鉴权）。
     *
     * <h3>为什么不直接让浏览器打开 /uploads/xxx.pdf</h3>
     * {@code /uploads/**} 在 SecurityConfig 里是 {@code permitAll} 的 —— 这是没办法的事：
     * 前端用 {@code img} / {@code a} 标签访问静态资源时，浏览器**不会带 Authorization 头**，
     * 不放行的话图片全裂。代价是「谁知道文件名，谁就能下载」，把链接转发给别人，
     * 没选课的人照样能拿到课件。
     *
     * <p>前端那句 {@code v-if="applyStatus === 1"} 只是把按钮藏起来，属于 **UI 遮蔽**，
     * 不是权限控制 —— 会按 F12 的人直接请求接口就绕过去了。
     *
     * <p>所以下载统一走这里：先用 JWT 里的身份复核「你是否真的有权看这门课的课件」，
     * 再把文件流读出来返回。附带两个收益：
     * <ol>
     *   <li>文件名可控制 —— 响应头按 RFC 5987 输出 {@code filename*}，中文名不会乱码；</li>
     *   <li>下载动作能被服务端感知，将来要做下载次数统计、审计日志都从这里接。</li>
     * </ol>
     *
     * <h3>inline 参数：预览和下载必须用不同的响应头</h3>
     * {@code inline=false}（默认，下载）：{@code application/octet-stream} +
     * {@code Content-Disposition: attachment} —— 浏览器只会在"下载记录"里落一条，不会打开它。
     *
     * <p>{@code inline=true}（预览）：必须换成**真实的 MIME 类型** +
     * {@code Content-Disposition: inline}。少了这一步会踩一个很隐蔽的坑：
     * 前端把响应包成 Blob 塞进 {@code iframe}，而 Blob 的类型直接来自响应头，
     * {@code octet-stream} 在浏览器眼里是"未知二进制"，iframe 渲染不了，
     * 于是点「预览」的表现和点「下载」一模一样（还多了一条下载记录）。
     *
     * <p>注意返回的是二进制流，不能用统一的 {@code Result<T>} 包装；
     * 但**业务失败仍然遵守项目约定**（HTTP 200 + JSON），前端据此区分
     * "这是一段错误 JSON" 还是 "这是真的文件"。
     */
    @GetMapping("/{resourceId}/download")
    public ResponseEntity<byte[]> download(@PathVariable Integer resourceId,
                                          @RequestParam(name = "inline", defaultValue = "false") boolean inline)
            throws IOException {
        CourseResource resource = service.getById(resourceId);
        if (resource == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "资源不存在或已被删除");
        }
        assertDownloadable(resource);

        String path = resource.getResourcePath();
        if (isExternal(path)) {
            throw new BizException(ResultCode.BIZ_ERROR, "这是外部链接，请用「打开链接」访问");
        }

        // 路径穿越防护：uploads 之外的文件一律不许读。
        // 先用 normalize 消掉 ".."，再断言结果仍在 base 目录内 —— 少了这一步，
        // resource_path 里写 ../../application.yml 就能把配置文件读走。
        Path base = Paths.get(uploadPath).toAbsolutePath().normalize();
        Path target = base.resolve(path.replaceFirst("^/uploads/", "")).normalize();
        if (!target.startsWith(base)) {
            throw new BizException(ResultCode.FORBIDDEN, "非法的资源路径");
        }
        if (!Files.isRegularFile(target)) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "文件不存在，可能已被清理");
        }

        byte[] bytes = Files.readAllBytes(target);
        String filename = downloadFileName(resource, target);
        // filename* 用 RFC 5987 编码（UTF-8 + 百分号转义），否则中文名在部分浏览器里是乱码
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentLength(bytes.length);
        if (inline) {
            // 预览：真实 MIME + inline，浏览器才会就地渲染而不是下载
            headers.setContentType(contentTypeOf(target));
            headers.set(HttpHeaders.CONTENT_DISPOSITION,
                    "inline; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded);
        } else {
            // 下载：统一走 octet-stream，避免浏览器把 pdf / 图片直接内嵌打开
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.set(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded);
        }
        return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
    }

    /**
     * 按物理文件的扩展名推断 MIME 类型，仅用于「预览」。
     *
     * <p>不做嗅探（读文件头判断），因为能上传的扩展名本来就被
     * {@code FileController} 的白名单限死了，扩展名可信；多读一次文件头没必要。
     * 认不出的类型退回 {@code octet-stream}，前端会走"不支持预览"的提示分支。
     */
    private MediaType contentTypeOf(Path target) {
        String name = target.getFileName().toString().toLowerCase();
        int dot = name.lastIndexOf('.');
        String ext = dot < 0 ? "" : name.substring(dot);
        return switch (ext) {
            case ".pdf" -> MediaType.APPLICATION_PDF;
            case ".png" -> MediaType.IMAGE_PNG;
            case ".jpg", ".jpeg" -> MediaType.IMAGE_JPEG;
            case ".gif" -> MediaType.IMAGE_GIF;
            case ".webp" -> MediaType.parseMediaType("image/webp");
            case ".bmp" -> MediaType.parseMediaType("image/bmp");
            case ".mp4" -> MediaType.parseMediaType("video/mp4");
            case ".webm" -> MediaType.parseMediaType("video/webm");
            case ".txt", ".md", ".csv" -> new MediaType("text", "plain", StandardCharsets.UTF_8);
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }

    /**
     * 谁能下载这门课的课件。
     *
     * <p>规则与「课程资源」页面看到的按钮完全一致，只是这次由服务端说了算：
     * <ul>
     *   <li>管理员：全部可见</li>
     *   <li>教师：只能是自己发布的课程</li>
     *   <li>学员：必须已通过该课程的报名审核（course_apply.audit_status = 1）</li>
     * </ul>
     */
    private void assertDownloadable(CourseResource resource) {
        if (CurrentUserUtil.isAdmin()) {
            return;
        }
        Integer uid = CurrentUserUtil.getId();
        Course course = courseMapper.selectById(resource.getCourseId());
        if (course == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "资源所属的课程不存在");
        }

        if ("teacher".equals(CurrentUserUtil.getRole())) {
            if (!Objects.equals(course.getPublishTeacherId(), uid)) {
                throw new BizException(ResultCode.FORBIDDEN, "只能下载自己发布课程的课件");
            }
            return;
        }

        Long passed = courseApplyMapper.selectCount(Wrappers.<CourseApply>lambdaQuery()
                .eq(CourseApply::getCourseId, resource.getCourseId())
                .eq(CourseApply::getStudentId, uid)
                .eq(CourseApply::getAuditStatus, 1));
        if (passed == null || passed == 0) {
            throw new BizException(ResultCode.FORBIDDEN, "你还没有通过这门课程的报名审核，暂时无法下载课件");
        }
    }

    /** 是否是外部链接：只认 http/https，其余都当作站内 /uploads 文件 */
    private boolean isExternal(String path) {
        if (!StringUtils.hasText(path)) {
            return false;
        }
        String lower = path.toLowerCase();
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    /**
     * 下载时的文件名。
     *
     * <p>资源名称是教师随手填的（可能不带扩展名），而扩展名直接影响
     * 操作系统用什么软件打开它，所以拿物理文件的扩展名兜底。
     */
    private String downloadFileName(CourseResource resource, Path target) {
        String name = StringUtils.hasText(resource.getResourceName())
                ? resource.getResourceName().trim()
                : "资源";
        String physical = target.getFileName().toString();
        int dot = physical.lastIndexOf('.');
        if (dot < 0) {
            return name;
        }
        String ext = physical.substring(dot);              // 含点，如 .pdf
        return name.toLowerCase().endsWith(ext.toLowerCase()) ? name : name + ext;
    }
}
