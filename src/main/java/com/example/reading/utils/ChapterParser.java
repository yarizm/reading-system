package com.example.reading.utils;

import com.example.reading.entity.SysChapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChapterParser {

    private static final Logger log = LoggerFactory.getLogger(ChapterParser.class);

    private static final Pattern CHAPTER_PATTERN = Pattern.compile("^\\s*(第[0-9零一二三四五六七八九十百千万]+[章节回]|Chapter\\s*\\d+|\\d+\\.)\\s*.*");

    public static List<SysChapter> parse(Long bookId, File file) {
        List<SysChapter> chapters = new ArrayList<>();

        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            String text = new String(bytes, StandardCharsets.UTF_8);
            if (text.contains("\uFFFD")) {
                text = new String(bytes, Charset.forName("GBK"));
            }

            String[] rawLines = text.split("\\r?\\n");

            SysChapter currentChapter = new SysChapter();
            currentChapter.setBookId(bookId);
            currentChapter.setTitle("序章 / 开始");
            currentChapter.setSort(0);

            StringBuilder contentBuilder = new StringBuilder();
            int sortIndex = 1;

            for (String rawLine : rawLines) {
                Matcher matcher = CHAPTER_PATTERN.matcher(rawLine);
                if (matcher.find()) {
                    String finalContent = processParagraphs(contentBuilder.toString());
                    currentChapter.setContent(finalContent);
                    currentChapter.setWordCount(finalContent.length());
                    chapters.add(currentChapter);

                    currentChapter = new SysChapter();
                    currentChapter.setBookId(bookId);
                    currentChapter.setTitle(rawLine.trim());
                    currentChapter.setSort(sortIndex++);
                    contentBuilder = new StringBuilder();
                } else {
                    contentBuilder.append(rawLine).append("\n");
                }
            }

            String finalContent = processParagraphs(contentBuilder.toString());
            currentChapter.setContent(finalContent);
            currentChapter.setWordCount(finalContent.length());
            chapters.add(currentChapter);
        } catch (Exception e) {
            log.error("Failed to parse chapter file. bookId={}, file={}",
                    bookId, file == null ? null : file.getAbsolutePath(), e);
        }
        return chapters;
    }

    private static String processParagraphs(String rawChapterContent) {
        String[] lines = rawChapterContent.split("\\n");
        StringBuilder result = new StringBuilder();
        StringBuilder paragraph = new StringBuilder();

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                if (!paragraph.isEmpty()) {
                    result.append("　　").append(paragraph).append("\n");
                    paragraph.setLength(0);
                }
                continue;
            }

            boolean hasIndent = line.startsWith("  ") || line.startsWith("　") || line.startsWith("\t");

            if (hasIndent) {
                if (!paragraph.isEmpty()) {
                    result.append("　　").append(paragraph).append("\n");
                    paragraph.setLength(0);
                }
                paragraph.append(trimmed);
            } else {
                paragraph.append(trimmed);
            }

            char lastChar = trimmed.charAt(trimmed.length() - 1);
            if (lastChar == '。' || lastChar == '！' || lastChar == '？' || lastChar == '”' || lastChar == '’' || lastChar == '"' || lastChar == '\'') {
                result.append("　　").append(paragraph).append("\n");
                paragraph.setLength(0);
            }
        }
        if (!paragraph.isEmpty()) {
            result.append("　　").append(paragraph).append("\n");
        }
        return result.toString();
    }
}
