package com.example.reading.service;

import com.example.reading.entity.SysBook;
import com.example.reading.entity.SysNote;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class ImportService {

    @Autowired
    private ISysBookService sysBookService;

    @Autowired
    private ISysNoteService sysNoteService;

    @Autowired
    private INoteReviewService noteReviewService;

    @Autowired(required = false)
    private NoteEsService noteEsService;

    public static class ImportItem {
        public String bookTitle;
        public String selectedText;
        public String content;
        public LocalDateTime createTime;
    }

    public static class ImportResult {
        public int imported;
        public int skipped;
        public List<String> errors = new ArrayList<>();
    }

    public ImportResult importFile(MultipartFile file, String format, Long userId) {
        ImportResult result = new ImportResult();
        List<ImportItem> items;

        try {
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            switch (format.toLowerCase()) {
                case "kindle" -> items = parseKindle(content);
                case "weread" -> items = parseWeRead(content);
                case "csv" -> items = parseCsv(content);
                case "json" -> {
                    try {
                        items = parseJson(content);
                    } catch (Exception e) {
                        result.errors.add("JSON 解析失败: " + e.getMessage());
                        return result;
                    }
                }
                default -> {
                    result.errors.add("不支持的格式: " + format);
                    return result;
                }
            }
        } catch (Exception e) {
            result.errors.add("文件读取失败: " + e.getMessage());
            return result;
        }

        for (ImportItem item : items) {
            try {
                Long bookId = findOrCreateBook(item.bookTitle, userId);

                SysNote note = new SysNote();
                note.setUserId(userId);
                note.setBookId(bookId);
                note.setSelectedText(item.selectedText != null ? item.selectedText : "");
                note.setContent(item.content != null ? item.content : "");
                note.setCreateTime(item.createTime != null ? item.createTime : LocalDateTime.now());
                sysNoteService.save(note);

                noteReviewService.autoAddToReview(userId, note.getId());
                if (noteEsService != null) noteEsService.syncNoteToEs(note);

                result.imported++;
            } catch (Exception e) {
                result.skipped++;
                result.errors.add("导入失败: " + e.getMessage());
            }
        }

        return result;
    }

    private Long findOrCreateBook(String title, Long userId) {
        if (title == null || title.trim().isEmpty()) title = "未分类导入";
        var query = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SysBook>();
        query.eq("title", title).eq("uploader_id", userId).last("LIMIT 1");
        SysBook book = sysBookService.getOne(query);
        if (book != null) return book.getId();

        book = new SysBook();
        book.setTitle(title);
        book.setAuthor("导入");
        book.setStatus(0);
        book.setUploaderId(userId);
        book.setCreateTime(LocalDateTime.now());
        sysBookService.save(book);
        return book.getId();
    }

    public List<ImportItem> parseKindle(String content) {
        List<ImportItem> items = new ArrayList<>();
        String[] entries = content.split("==========");
        for (String entry : entries) {
            entry = entry.trim();
            if (entry.isEmpty()) continue;
            String[] lines = entry.split("\n");
            if (lines.length < 3) continue;

            String titleLine = lines[0].trim();
            String bookTitle = titleLine;
            int parenIdx = titleLine.lastIndexOf("(");
            if (parenIdx > 0) bookTitle = titleLine.substring(0, parenIdx).trim();

            StringBuilder contentBuilder = new StringBuilder();
            for (int i = 2; i < lines.length; i++) {
                if (!lines[i].trim().isEmpty()) contentBuilder.append(lines[i].trim());
            }

            if (contentBuilder.length() > 0) {
                ImportItem item = new ImportItem();
                item.bookTitle = bookTitle;
                item.selectedText = contentBuilder.toString();
                item.content = "";
                items.add(item);
            }
        }
        return items;
    }

    public List<ImportItem> parseWeRead(String content) {
        List<ImportItem> items = new ArrayList<>();
        String[] sections = content.split("(?=^## )", Pattern.MULTILINE);
        String currentBook = "微信读书导入";

        for (String section : sections) {
            section = section.trim();
            if (section.isEmpty()) continue;
            String[] lines = section.split("\n");

            if (lines[0].startsWith("## ")) {
                currentBook = lines[0].substring(3).trim();
                continue;
            }

            for (int i = 0; i < lines.length; i++) {
                if (lines[i].startsWith("> ")) {
                    ImportItem item = new ImportItem();
                    item.bookTitle = currentBook;
                    item.selectedText = lines[i].substring(2).trim();

                    StringBuilder noteContent = new StringBuilder();
                    for (int j = i + 1; j < lines.length; j++) {
                        String line = lines[j].trim();
                        if (line.isEmpty() || line.startsWith("> ")) break;
                        noteContent.append(line).append("\n");
                    }
                    item.content = noteContent.toString().trim();
                    items.add(item);
                }
            }
        }
        return items;
    }

    public List<ImportItem> parseCsv(String content) {
        List<ImportItem> items = new ArrayList<>();
        String[] lines = content.split("\n");
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            String[] fields = parseCsvLine(line);
            if (fields.length < 2) continue;

            ImportItem item = new ImportItem();
            item.bookTitle = fields[0];
            item.selectedText = fields.length > 1 ? fields[1] : "";
            item.content = fields.length > 2 ? fields[2] : "";
            items.add(item);
        }
        return items;
    }

    private String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (char c : line.toCharArray()) {
            if (c == '"') inQuotes = !inQuotes;
            else if (c == ',' && !inQuotes) { fields.add(current.toString().trim()); current = new StringBuilder(); }
            else current.append(c);
        }
        fields.add(current.toString().trim());
        return fields.toArray(new String[0]);
    }

    public List<ImportItem> parseJson(String content) throws Exception {
        List<ImportItem> items = new ArrayList<>();
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(content);
        if (root.isArray()) {
            for (com.fasterxml.jackson.databind.JsonNode node : root) {
                ImportItem item = new ImportItem();
                item.bookTitle = node.has("bookTitle") ? node.get("bookTitle").asText() : "JSON 导入";
                item.selectedText = node.has("selectedText") ? node.get("selectedText").asText() : "";
                item.content = node.has("content") ? node.get("content").asText() : "";
                items.add(item);
            }
        }
        return items;
    }
}
