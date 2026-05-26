package com.example.reading.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.reading.entity.SysBook;
import com.example.reading.entity.SysChapter;
import com.example.reading.mapper.SysChapterMapper;
import com.example.reading.service.AuthContextService;
import com.example.reading.service.ISysBookService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/files")
public class FileController {

    private static final Pattern CHAPTER_TTS_PATTERN = Pattern.compile("^chapter_tts_[^_]+_(\\d+)_.*");
    private static final Set<String> PUBLIC_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "webp", "gif", "mp3", "wav", "m4a", "ogg"
    );

    private final ISysBookService sysBookService;
    private final SysChapterMapper chapterMapper;
    private final AuthContextService authContextService;

    @Value("${file.upload-path}")
    private String uploadPath;

    public FileController(ISysBookService sysBookService,
                          SysChapterMapper chapterMapper,
                          AuthContextService authContextService) {
        this.sysBookService = sysBookService;
        this.chapterMapper = chapterMapper;
        this.authContextService = authContextService;
    }

    @GetMapping("/{fileName:.+}")
    public ResponseEntity<Resource> getFile(@PathVariable String fileName,
                                            @RequestParam(required = false) String token,
                                            HttpServletRequest request) throws Exception {
        Path basePath = Paths.get(uploadPath).toAbsolutePath().normalize();
        Path filePath = basePath.resolve(fileName).normalize();
        if (!filePath.startsWith(basePath) || !Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            return ResponseEntity.notFound().build();
        }

        if (!canAccess(fileName, request, token)) {
            return ResponseEntity.status(403).build();
        }

        Resource resource = new UrlResource(filePath.toUri());
        String contentType = Files.probeContentType(filePath);
        if (contentType == null) {
            contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(fileName, StandardCharsets.UTF_8).build().toString())
                .body(resource);
    }

    private boolean canAccess(String fileName, HttpServletRequest request, String token) {
        if (fileName.startsWith("chapter_tts_")) {
            return canAccessChapterTts(fileName, request, token);
        }

        String extension = extensionOf(fileName);
        if (PUBLIC_EXTENSIONS.contains(extension)) {
            return true;
        }

        if ("txt".equals(extension)) {
            return canAccessBookFile(fileName, request, token);
        }

        return false;
    }

    private boolean canAccessChapterTts(String fileName, HttpServletRequest request, String token) {
        Matcher matcher = CHAPTER_TTS_PATTERN.matcher(fileName);
        if (!matcher.matches()) {
            return false;
        }
        SysChapter chapter = chapterMapper.selectById(Long.valueOf(matcher.group(1)));
        if (chapter == null) {
            return false;
        }
        SysBook book = sysBookService.getById(chapter.getBookId());
        return canViewBook(book, request, token);
    }

    private boolean canAccessBookFile(String fileName, HttpServletRequest request, String token) {
        QueryWrapper<SysBook> query = new QueryWrapper<>();
        query.eq("file_path", "/files/" + fileName);
        List<SysBook> books = sysBookService.list(query);
        if (books == null || books.isEmpty()) {
            return false;
        }
        return books.stream().anyMatch(book -> canViewBook(book, request, token));
    }

    private boolean canViewBook(SysBook book, HttpServletRequest request, String token) {
        if (book == null) return false;
        if (authContextService.isPublicBook(book)) return true;
        Long currentUserId = authContextService.currentUserId(request);
        if (currentUserId == null && token != null && !token.isBlank()) {
            currentUserId = authContextService.currentUserId(token);
        }
        return authContextService.canViewBook(book, currentUserId);
    }

    private String extensionOf(String fileName) {
        int index = fileName.lastIndexOf('.');
        if (index < 0 || index == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(index + 1).toLowerCase();
    }
}
