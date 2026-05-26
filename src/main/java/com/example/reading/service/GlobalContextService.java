package com.example.reading.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.reading.entity.SysBook;
import com.example.reading.entity.UserBookshelf;
import com.example.reading.entity.SysNote;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class GlobalContextService {

    @Autowired
    private IUserBookshelfService userBookshelfService;

    @Autowired
    private ISysBookService sysBookService;

    @Autowired
    private ISysNoteService sysNoteService;

    public Map<String, Object> buildGlobalContext(Long userId) {
        Map<String, Object> context = new HashMap<>();

        // 书架统计
        QueryWrapper<UserBookshelf> shelfQuery = new QueryWrapper<>();
        shelfQuery.eq("user_id", userId);
        List<UserBookshelf> bookshelfList = userBookshelfService.list(shelfQuery);
        
        long readingCount = bookshelfList.stream().filter(b -> b.getIsFinished() == null || b.getIsFinished() == 0).count();
        long finishedCount = bookshelfList.stream().filter(b -> b.getIsFinished() != null && b.getIsFinished() == 1).count();
        
        context.put("books_reading_count", readingCount);
        context.put("books_finished_count", finishedCount);

        // 获取最近在读的书籍
        UserBookshelf lastRead = bookshelfList.stream()
                .filter(b -> b.getLastReadTime() != null)
                .max((b1, b2) -> b1.getLastReadTime().compareTo(b2.getLastReadTime()))
                .orElse(null);
                
        if (lastRead != null) {
            SysBook lastReadBook = sysBookService.getById(lastRead.getBookId());
            if (lastReadBook != null) {
                context.put("last_read_book_title", lastReadBook.getTitle());
                context.put("last_read_book_id", lastReadBook.getId());
            }
        }

        // 笔记统计
        QueryWrapper<SysNote> noteQuery = new QueryWrapper<>();
        noteQuery.eq("user_id", userId);
        long totalNotes = sysNoteService.count(noteQuery);
        context.put("total_notes_count", totalNotes);

        // 系统功能手册
        context.put("system_features", "1. 书架管理: /shelf\n2. 阅读书籍: /read/{id}\n3. 我的上传: /my-books\n4. 个人资料: /profile\n5. 阅读洞察: /insights");

        return context;
    }
}
