package com.example.reading.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.reading.entity.SysBook;
import com.example.reading.entity.SysNote;
import com.example.reading.entity.UserBookshelf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ReadingContextService {

    @Autowired
    private ISysNoteService sysNoteService;

    @Autowired
    private IUserBookshelfService userBookshelfService;

    @Autowired
    private ISysBookService sysBookService;

    public Map<String, Object> buildReadingContext(Long userId, Long bookId) {
        Map<String, Object> context = new HashMap<>();

        if (bookId != null && userId != null) {
            // 获取书籍信息
            SysBook book = sysBookService.getById(bookId);
            if (book != null) {
                context.put("book_title", book.getTitle());
                context.put("book_author", book.getAuthor());
            }

            // 获取阅读进度
            QueryWrapper<UserBookshelf> shelfWrapper = new QueryWrapper<>();
            shelfWrapper.eq("user_id", userId).eq("book_id", bookId);
            UserBookshelf bookshelf = userBookshelfService.getOne(shelfWrapper);
            if (bookshelf != null) {
                String progress = "当前在第 " + bookshelf.getCurrentChapterIndex() + " 章";
                if (bookshelf.getIsFinished() != null && bookshelf.getIsFinished() == 1) {
                    progress = "已读完";
                }
                context.put("reading_progress", progress);
            } else {
                context.put("reading_progress", "未加入书架或无进度");
            }

            // 获取最近笔记
            QueryWrapper<SysNote> noteWrapper = new QueryWrapper<>();
            noteWrapper.eq("user_id", userId).eq("book_id", bookId)
                    .orderByDesc("create_time").last("LIMIT 5");
            List<SysNote> recentNotes = sysNoteService.list(noteWrapper);
            if (recentNotes != null && !recentNotes.isEmpty()) {
                String notesStr = recentNotes.stream()
                        .map(n -> "选文: " + n.getSelectedText() + " | 笔记: " + n.getContent())
                        .collect(Collectors.joining("\n---\n"));
                context.put("recent_notes", notesStr);
                context.put("notes_count", recentNotes.size());
            } else {
                context.put("recent_notes", "暂无笔记");
                context.put("notes_count", 0);
            }
        }

        return context;
    }
}
