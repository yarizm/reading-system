package com.example.reading.utils;

import com.example.reading.entity.SysChapter;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChapterParser {

    // 正则匹配：第xxxxx章/节/回，或者纯数字章节
    private static final Pattern CHAPTER_PATTERN = Pattern.compile("^\\s*(第[0-9零一二三四五六七八九十百千]+[章回节]|Chapter\\s*\\d+|\\d+\\.)\\s*.*");

    public static List<SysChapter> parse(Long bookId, File file) {
        List<SysChapter> chapters = new ArrayList<>();

        try {
            // 解决乱码问题：探测文件编码（UTF-8 或 GBK）
            byte[] bytes = Files.readAllBytes(file.toPath());
            String text = new String(bytes, StandardCharsets.UTF_8);
            if (text.contains("")) {
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
                // 判断是否是章节标题
                Matcher matcher = CHAPTER_PATTERN.matcher(rawLine);
                if (matcher.find()) {
                    // 保存上一章
                    String finalContent = processParagraphs(contentBuilder.toString());
                    currentChapter.setContent(finalContent);
                    currentChapter.setWordCount(finalContent.length());
                    chapters.add(currentChapter);

                    // 开启新一章
                    currentChapter = new SysChapter();
                    currentChapter.setBookId(bookId);
                    currentChapter.setTitle(rawLine.trim());
                    currentChapter.setSort(sortIndex++);
                    contentBuilder = new StringBuilder();
                } else {
                    // 收集普通行（先不处理段落，只是追加，使用原始行分隔，后续统一处理死断行）
                    contentBuilder.append(rawLine).append("\n");
                }
            }
            
            // 保存最后一章
            String finalContent = processParagraphs(contentBuilder.toString());
            currentChapter.setContent(finalContent);
            currentChapter.setWordCount(finalContent.length());
            chapters.add(currentChapter);

        } catch (Exception e) {
            e.printStackTrace();
        }
        return chapters;
    }

    /**
     * 处理硬回车、固定字数被切断段落的问题。
     */
    private static String processParagraphs(String rawChapterContent) {
        String[] lines = rawChapterContent.split("\\n");
        StringBuilder result = new StringBuilder();
        StringBuilder paragraph = new StringBuilder();

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                if (paragraph.length() > 0) {
                    result.append("　　").append(paragraph).append("\n");
                    paragraph.setLength(0);
                }
                continue;
            }

            // 如果这一行有自然的段落缩进（通常开头是几个空格或全角空格），则认为它是一个新段落的开始
            boolean hasIndent = line.startsWith("  ") || line.startsWith("　") || line.startsWith("\t");

            if (hasIndent) {
                if (paragraph.length() > 0) {
                    result.append("　　").append(paragraph).append("\n");
                    paragraph.setLength(0);
                }
                paragraph.append(trimmed);
            } else {
                // 没有缩进，它可能是上一段的硬回车截断延续。我们和上一句拼接在一起。
                paragraph.append(trimmed);
            }

            // 如果句子以明确的标点结尾，也认为是一段的结束，防止整个章节变成一大段
            char lastChar = trimmed.charAt(trimmed.length() - 1);
            if (lastChar == '。' || lastChar == '！' || lastChar == '？' || lastChar == '”' || lastChar == '…' || lastChar == '’' || lastChar == '\"' || lastChar == '\'') {
                result.append("　　").append(paragraph).append("\n");
                paragraph.setLength(0);
            }
        }
        if (paragraph.length() > 0) {
            result.append("　　").append(paragraph).append("\n");
        }
        return result.toString();
    }
}