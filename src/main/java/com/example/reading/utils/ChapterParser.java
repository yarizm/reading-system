package com.example.reading.utils;

import com.example.reading.entity.SysChapter;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChapterParser {

    // 正则匹配：第xxxxx章/节/回，或者纯数字章节
    // 比如： "第1章", "第一章", "Chapter 1", "1."
    private static final Pattern CHAPTER_PATTERN = Pattern.compile("^\\s*(第[0-9零一二三四五六七八九十百千]+[章回节]|Chapter\\s*\\d+|\\d+\\.)\\s*.*");

    public static List<SysChapter> parse(Long bookId, File file) {
        List<SysChapter> chapters = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            SysChapter currentChapter = new SysChapter();
            currentChapter.setBookId(bookId);
            currentChapter.setTitle("序章 / 开始"); // 默认第一章
            currentChapter.setSort(0);
            StringBuilder contentBuilder = new StringBuilder();

            int sortIndex = 1;

            while ((line = reader.readLine()) != null) {
                Matcher matcher = CHAPTER_PATTERN.matcher(line);
                if (matcher.find()) {
                    // === 发现新章节 ===
                    // 1. 保存上一章
                    currentChapter.setContent(contentBuilder.toString());
                    currentChapter.setWordCount(contentBuilder.length());
                    chapters.add(currentChapter);

                    // 2. 开启新一章
                    currentChapter = new SysChapter();
                    currentChapter.setBookId(bookId);
                    currentChapter.setTitle(line.trim()); // 标题就是这一行
                    currentChapter.setSort(sortIndex++);
                    contentBuilder = new StringBuilder(); // 清空内容缓冲
                } else {
                    // 不是标题，追加到当前章节内容
                    contentBuilder.append(line).append("\n");
                }
            }
            // 保存最后一章
            currentChapter.setContent(contentBuilder.toString());
            currentChapter.setWordCount(contentBuilder.length());
            chapters.add(currentChapter);

        } catch (Exception e) {
            e.printStackTrace();
        }
        return chapters;
    }
}