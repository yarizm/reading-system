package com.example.reading.controller;

import com.example.reading.common.Result;
import com.example.reading.service.BookSearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Elasticsearch 全文搜索控制器
 */
@RestController
@RequestMapping("/search")
public class SearchController {

    @Autowired
    private BookSearchService bookSearchService;

    /**
     * 全文搜索接口
     * GET /api/search?keyword=xxx&category=xxx&pageNum=1&pageSize=12
     */
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

    /**
     * 全量同步 MySQL → Elasticsearch（管理员手动触发）
     * POST /api/search/sync
     */
    @PostMapping("/sync")
    public Result<?> syncAll() {
        int count = bookSearchService.syncAllBooksToEs();
        return Result.success("成功同步 " + count + " 本书到 Elasticsearch 索引");
    }
}
