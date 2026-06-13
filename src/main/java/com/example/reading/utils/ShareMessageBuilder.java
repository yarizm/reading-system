package com.example.reading.utils;

import com.example.reading.dto.AudioShareRequest;
import com.example.reading.dto.ParagraphShareRequest;
import com.example.reading.entity.BookShare;
import com.example.reading.entity.SysBook;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ShareMessageBuilder {

    public static final String BOOK_SHARE_PREFIX = "__BOOK_SHARE__";
    public static final String AUDIO_SHARE_PREFIX = "__AUDIO_SHARE__";
    public static final String PARAGRAPH_SHARE_PREFIX = "__PARAGRAPH_SHARE__";
    public static final String DEFAULT_AUDIO_TITLE = "朗读音频";
    public static final String DEFAULT_AUDIO_SOURCE_TYPE = "paragraph";
    public static final String DEFAULT_PARAGRAPH_BOOK_TITLE = "当前书籍";

    private static final Gson GSON = new GsonBuilder().serializeNulls().create();

    private ShareMessageBuilder() {
    }

    public static String buildBookShareContent(BookShare share, SysBook book) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("shareId", share.getId());
        payload.put("bookId", share.getBookId());
        payload.put("bookTitle", book == null ? "" : book.getTitle());
        payload.put("bookAuthor", book == null ? "" : book.getAuthor());
        payload.put("coverUrl", book == null ? "" : book.getCoverUrl());
        payload.put("message", share.getMessage() == null ? "" : share.getMessage().trim());
        return BOOK_SHARE_PREFIX + GSON.toJson(payload);
    }

    public static String buildAudioShareContent(AudioShareRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("audioUrl", request.getAudioUrl());
        payload.put("title", defaultValue(request.getTitle(), DEFAULT_AUDIO_TITLE));
        payload.put("sourceType", defaultValue(request.getSourceType(), DEFAULT_AUDIO_SOURCE_TYPE));
        payload.put("bookId", request.getBookId());
        payload.put("chapterIndex", request.getChapterIndex());
        payload.put("paragraphIndex", request.getParagraphIndex());
        payload.put("message", defaultValue(request.getMessage(), ""));
        return AUDIO_SHARE_PREFIX + GSON.toJson(payload);
    }

    public static String buildParagraphShareContent(String bookTitle, ParagraphShareRequest request, String quote) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("bookId", request.getBookId());
        payload.put("bookTitle", bookTitle);
        payload.put("chapterIndex", request.getChapterIndex());
        payload.put("paragraphIndex", request.getParagraphIndex());
        payload.put("quote", truncate(normalizeParagraphText(quote), 180));
        payload.put("message", truncate(normalizeParagraphText(request.getMessage()), 120));
        return PARAGRAPH_SHARE_PREFIX + GSON.toJson(payload);
    }

    public static String normalizeParagraphText(String text) {
        return text == null ? "" : text.replace("\r", " ").replace("\n", " ").trim();
    }

    private static String defaultValue(String text, String fallback) {
        return text == null ? fallback : text.trim();
    }

    private static String truncate(String text, int maxLength) {
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }
}
