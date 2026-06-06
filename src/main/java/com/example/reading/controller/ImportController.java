package com.example.reading.controller;

import com.example.reading.common.Result;
import com.example.reading.service.AuthContextService;
import com.example.reading.service.ImportService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/import")
public class ImportController {

    @Autowired
    private ImportService importService;

    @Autowired
    private AuthContextService authContextService;

    @PostMapping("/upload")
    public Result<?> upload(@RequestParam("file") MultipartFile file,
                            @RequestParam("format") String format,
                            HttpServletRequest request) {
        Long userId = authContextService.currentUserId(request);
        if (userId == null) return Result.error("403", "Forbidden");
        if (file.isEmpty()) return Result.error("400", "文件为空");

        ImportService.ImportResult result = importService.importFile(file, format, userId);
        return Result.success(result);
    }
}
