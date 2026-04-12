package com.example.reading.controller;

import com.example.reading.common.Result;
import com.example.reading.service.BookSearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 全文搜索控制器
 * 基于 Elasticsearch 提供书籍元数据和章节内容的全文检索，以及 MySQL → ES 的全量同步入口。
 */
@RestController
@RequestMapping("/search")
public class SearchController {

    @Autowired
    private BookSearchService bookSearchService;

    /** 全文搜索（支持关键词 + 分类筛选 + 分页） */
    @GetMapping
    public Result<?> search(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "") String category,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "12") Integer pageSize) {

        if (keyword.isEmpty()) {
            return Result.error("400", "搜索关键词不能为空");
        }

        Map<String, Object> result = bookSearchService.search(keyword, category, pageNum, pageSize);
        return Result.success(result);
    }

    /** 全量同步 MySQL 数据到 Elasticsearch（管理员手动触发） */
    @PostMapping("/sync")
    public Result<?> syncAll() {
        int count = bookSearchService.syncAllBooksToEs();
        return Result.success("成功同步 " + count + " 本书到 Elasticsearch 索引");
    }
}
