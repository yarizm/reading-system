package com.example.reading.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.reading.entity.SysNote;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class NoteAiService {
    @Autowired
    private ISysNoteService sysNoteService;

    public String buildBookNotesContext(Long userId, Long bookId) {
        QueryWrapper<SysNote> query = new QueryWrapper<>();
        query.eq("user_id", userId);
        query.eq("book_id", bookId);
        query.orderByAsc("id");
        
        List<SysNote> notes = sysNoteService.list(query);
        if (notes.isEmpty()) return "无笔记";
        
        return notes.stream()
                .map(n -> String.format("- 摘录:%s | 笔记内容:%s", 
                        n.getSelectedText() != null ? n.getSelectedText() : "无摘录", 
                        n.getContent() != null ? n.getContent() : "无心得"))
                .collect(Collectors.joining("\n"));
    }
}
