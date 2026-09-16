package com.online.study.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/file")
@CrossOrigin
public class FileController {

    @Value("${file.upload.path:uploads/}")
    private String uploadPath;

    @PostMapping("/upload")
    public Map<String, Object> upload(@RequestParam("file") MultipartFile file) {
        Map<String, Object> result = new HashMap<>();
        if (file.isEmpty()) {
            result.put("success", false);
            result.put("message", "文件为空");
            return result;
        }

        try {
            // 获取文件名并生成新文件名
            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String newFileName = UUID.randomUUID().toString() + extension;

            // 确保目录存在, 获取绝对路径
            File dir = new File(System.getProperty("user.dir"), uploadPath);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            // 保存文件
            File dest = new File(dir, newFileName);
            file.transferTo(dest);

            // 返回相对路径，便于本地开发和 Nginx 代理环境统一访问
            String url = "/uploads/" + newFileName;
            result.put("success", true);
            result.put("url", url);
            return result;

        } catch (IOException e) {
            e.printStackTrace();
            result.put("success", false);
            result.put("message", "文件上传失败");
            return result;
        }
    }
}
