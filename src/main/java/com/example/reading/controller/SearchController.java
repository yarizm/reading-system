package com.example.reading.controller;

import com.example.reading.common.Result;
import com.example.reading.service.AuthContextService;
import com.example.reading.service.BookSearchService;
import com.example.reading.utils.PaginationUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import com.example.reading.entity.SysBook;
import com.example.reading.mapper.SysBookMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.HashMap;
import java.util.Map;

/**
 * 全文搜索控制器
 * 基于 Elasticsearch 提供书籍元数据和章节内容的全文检索，以及 MySQL → ES 的全量同步入口。
 */
@RestController
@RequestMapping("/search")
public class SearchController {

    @Autowired(required = false)
    private BookSearchService bookSearchService;

    @Autowired
    private SysBookMapper sysBookMapper;

    @Autowired
    private AuthContextService authContextService;

    @Autowired
    private com.example.reading.service.DifyKnowledgeBaseService difyKnowledgeBaseService;

    private boolean isAdmin(HttpServletRequest request) {
        return authContextService.isAdmin(request);
    }

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

        if (bookSearchService != null) {
            try {
                Map<String, Object> result = bookSearchService.search(keyword, category, pageNum, pageSize);
                return Result.success(result);
            } catch (Exception e) {
                // Ignore and fallback to MySQL
            }
        }

        // MySQL fallback search when ES is not available
        QueryWrapper<SysBook> query = new QueryWrapper<>();
        query.eq("status", 2);
        query.and(w -> w.like("title", keyword).or().like("author", keyword).or().like("description", keyword));
        if (category != null && !category.isEmpty() && !"全部".equals(category)) {
            query.eq("category", category);
        }
        
        Page<SysBook> page = new Page<>(PaginationUtils.pageNum(pageNum), PaginationUtils.pageSize(pageSize));
        sysBookMapper.selectPage(page, query);
        
        Map<String, Object> result = new HashMap<>();
        result.put("total", page.getTotal());
        result.put("records", page.getRecords());
        
        return Result.success(result);
    }

    /** 全量同步 MySQL 数据到 Elasticsearch（管理员手动触发） */
    @PostMapping("/sync")
    public Result<?> syncAll(HttpServletRequest request) {
        if (!isAdmin(request)) {
            return Result.error("403", "Forbidden");
        }
        if (bookSearchService == null) {
            return Result.error("503", "Elasticsearch 未启用，不支持同步操作");
        }
        int count = bookSearchService.syncAllBooksToEs();
        return Result.success("成功同步 " + count + " 本书到 Elasticsearch 索引");
    }

    /** 全量同步已公开书籍到 Dify Knowledge Base（管理员手动触发） */
    @PostMapping("/syncKb")
    public Result<?> syncAllKb(HttpServletRequest request) {
        if (!isAdmin(request)) {
            return Result.error("403", "Forbidden");
        }
        difyKnowledgeBaseService.syncAllBooksToKb();
        return Result.success("已触发全量 KB 同步（异步执行），请查看日志获取进度");
    }
}
