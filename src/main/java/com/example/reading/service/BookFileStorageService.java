package com.example.reading.service;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

@Service
public class BookFileStorageService {

    private static final Set<String> ALLOWED_SUFFIXES = Set.of("jpg", "jpeg", "png", "webp", "gif", "txt");

    @Value("${file.upload-path}")
    private String uploadPath;

    public String storeUpload(MultipartFile file) throws IOException {
        String suffix = FileUtil.getSuffix(file.getOriginalFilename());
        if (!isAllowedSuffix(suffix)) {
            throw new UnsupportedFileTypeException();
        }

        String fileName = IdUtil.fastSimpleUUID() + "." + suffix;
        File dir = new File(uploadPath);
        if (!dir.exists() && !dir.mkdirs() && !dir.exists()) {
            throw new IOException("Failed to create upload directory: " + uploadPath);
        }

        file.transferTo(new File(dir, fileName));
        return "/files/" + fileName;
    }

    public File resolveStoredFile(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            throw new IllegalArgumentException("filePath cannot be blank");
        }

        String normalizedFilePath = filePath.replace('\\', '/');
        String fileName = normalizedFilePath.substring(normalizedFilePath.lastIndexOf("/") + 1);
        if (fileName.isBlank() || fileName.contains("/") || fileName.contains("\\")) {
            throw new IllegalArgumentException("Invalid file path");
        }

        Path basePath = Path.of(uploadPath).toAbsolutePath().normalize();
        Path resolvedPath = basePath.resolve(fileName).normalize();
        if (!resolvedPath.startsWith(basePath)) {
            throw new IllegalArgumentException("Invalid file path");
        }
        return resolvedPath.toFile();
    }

    private boolean isAllowedSuffix(String suffix) {
        return suffix != null && ALLOWED_SUFFIXES.contains(suffix.toLowerCase());
    }

    public static class UnsupportedFileTypeException extends RuntimeException {
    }
}
