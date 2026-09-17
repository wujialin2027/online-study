package com.online.study.controller;

import com.online.study.common.Result;
import com.online.study.common.ResultCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 文件上传
 *
 * <p>改造点：
 * <ol>
 *   <li><b>加扩展名白名单</b>。原代码只判断"文件非空"，扩展名直接取自原始文件名，
 *       于是可以上传 {@code .html} / {@code .svg} 这类<b>会被浏览器当页面执行</b>的文件，
 *       拿到链接后诱导别人点开就是存储型 XSS。</li>
 *   <li><b>返回统一 Result</b>，data 形如 {@code {"url": "/uploads/xxx.pdf", "name": "原文件名"}}</li>
 *   <li><b>UUID 去横线</b>后重命名：既避免同名覆盖，也彻底消除原始文件名里的
 *       路径穿越字符（{@code ../}）带来的风险。</li>
 *   <li>异常改用日志框架输出（原来用 {@code e.printStackTrace()}），
 *       并且不再把异常细节透给前端。</li>
 *   <li>去掉 {@code @CrossOrigin}：跨域已由 SecurityConfig 统一配置，
 *       分散在各类上加注解容易出现"某个接口的跨域规则和其他不一致"的隐患。</li>
 * </ol>
 */
@RestController
@RequestMapping("/file")
public class FileController {

    private static final Logger log = LoggerFactory.getLogger(FileController.class);

    /**
     * 允许上传的扩展名白名单（小写、含点）。
     * 采用「白名单」而不是「黑名单」：黑名单永远列不全，白名单默认拒绝。
     */
    private static final List<String> ALLOWED_EXTENSIONS = List.of(
            // 图片
            ".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp",
            // 文档
            ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx",
            ".txt", ".md", ".csv",
            // 压缩包
            ".zip", ".rar", ".7z"
    );

    @Value("${file.upload.path:uploads/}")
    private String uploadPath;

    @PostMapping("/upload")
    public Result<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return Result.failure(ResultCode.PARAM_ERROR, "文件为空");
        }

        String originalFilename = file.getOriginalFilename();
        String extension = extractExtension(originalFilename);

        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            return Result.failure(ResultCode.PARAM_ERROR,
                    "不支持的文件类型（" + (extension.isEmpty() ? "无扩展名" : extension)
                            + "），仅支持图片、文档与压缩包");
        }

        try {
            File dir = new File(System.getProperty("user.dir"), uploadPath);
            if (!dir.exists() && !dir.mkdirs()) {
                log.error("创建上传目录失败：{}", dir.getAbsolutePath());
                return Result.failure(ResultCode.SYSTEM_ERROR, "服务器存储目录不可用");
            }

            String newFileName = UUID.randomUUID().toString().replace("-", "") + extension;
            File dest = new File(dir, newFileName);
            file.transferTo(dest);

            Map<String, String> data = new HashMap<>();
            data.put("url", "/uploads/" + newFileName);
            data.put("name", originalFilename);
            return Result.success(data);

        } catch (IOException e) {
            // 记录完整堆栈，但不把内部细节返回给前端
            log.error("文件上传失败：{}", originalFilename, e);
            return Result.failure(ResultCode.SYSTEM_ERROR, "文件上传失败，请稍后重试");
        }
    }

    /** 提取小写扩展名（含点）；无扩展名返回空串 */
    private String extractExtension(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot).toLowerCase(Locale.ROOT);
    }
}
