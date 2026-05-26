package com.example.reading.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.reading.entity.SysBook;
import com.example.reading.entity.SysNote;
import com.example.reading.entity.UserBookshelf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class InsightService {

    @Autowired
    private IUserBookshelfService userBookshelfService;

    @Autowired
    private ISysNoteService sysNoteService;

    @Autowired
    private ISysBookService sysBookService;

    public Map<String, Object> collectUserReadingStats(Long userId) {
        Map<String, Object> stats = new HashMap<>();

        // 获取书架
        QueryWrapper<UserBookshelf> shelfQuery = new QueryWrapper<>();
        shelfQuery.eq("user_id", userId);
        List<UserBookshelf> bookshelfList = userBookshelfService.list(shelfQuery);
        
        long finishedCount = bookshelfList.stream().filter(b -> b.getIsFinished() != null && b.getIsFinished() == 1).count();
        long readingCount = bookshelfList.stream().filter(b -> b.getIsFinished() == null || b.getIsFinished() == 0).count();

        stats.put("total_books", bookshelfList.size());
        stats.put("finished_books", finishedCount);
        stats.put("reading_books", readingCount);

        // 获取最近在读的书籍
        List<String> readingBookTitles = bookshelfList.stream()
                .filter(b -> b.getIsFinished() == null || b.getIsFinished() == 0)
                .sorted((b1, b2) -> {
                    LocalDateTime t1 = b1.getLastReadTime() != null ? b1.getLastReadTime() : LocalDateTime.MIN;
                    LocalDateTime t2 = b2.getLastReadTime() != null ? b2.getLastReadTime() : LocalDateTime.MIN;
                    return t2.compareTo(t1);
                })
                .limit(3)
                .map(b -> {
                    SysBook sysBook = sysBookService.getById(b.getBookId());
                    String progressStr = b.getCurrentChapterIndex() != null ? "第" + (b.getCurrentChapterIndex() + 1) + "章" : "未知";
                    return sysBook != null ? sysBook.getTitle() + "(进度:" + progressStr + ")" : "未知书籍";
                })
                .collect(Collectors.toList());

        stats.put("recent_reading_books", String.join(", ", readingBookTitles));

        // 获取笔记
        QueryWrapper<SysNote> noteQuery = new QueryWrapper<>();
        noteQuery.eq("user_id", userId);
        noteQuery.orderByDesc("create_time");
        List<SysNote> notes = sysNoteService.list(noteQuery);

        stats.put("total_notes", notes.size());
        
        // 获取最近笔记内容
        List<String> recentNotes = notes.stream()
                .limit(5)
                .map(n -> String.format("[笔记] %s", n.getContent() != null ? n.getContent() : n.getSelectedText()))
                .collect(Collectors.toList());
        
        stats.put("recent_notes_preview", String.join(" | ", recentNotes));

        return stats;
    }
}
