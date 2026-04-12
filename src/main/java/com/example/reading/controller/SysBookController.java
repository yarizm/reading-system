package com.example.reading.controller;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.reading.common.Result;
import com.example.reading.entity.SysBook;
import com.example.reading.entity.SysChapter;
import com.example.reading.entity.UserBookshelf;
import com.example.reading.entity.SysUser;
import com.example.reading.service.IBookRecommendationService;
import com.example.reading.service.ISysBookService;
import com.example.reading.service.ISysUserService;
import com.example.reading.utils.ChapterParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 书籍管理控制器
 * 提供书籍的增删改查、用户上传、管理员审核、目录章节读取、热门榜单、随机推荐等功能。
 */
@RestController
@RequestMapping("/sysBook")
public class SysBookController {

    @Autowired
    private ISysBookService sysBookService;

    @Autowired
    private com.example.reading.service.BookSearchService bookSearchService;

    @Autowired
    private com.example.reading.mapper.SysCommentMapper commentMapper;

    @Autowired
    private com.example.reading.mapper.SysChapterMapper chapterMapper;

    @Autowired
    private com.example.reading.mapper.SysBookMapper sysBookMapper;

    @Autowired
    private com.example.reading.mapper.UserBookshelfMapper bookshelfMapper;

    @Autowired
    private IBookRecommendationService bookRecommendationService;

    @Autowired
    private ISysUserService sysUserService;

    @Value("${file.upload-path}")
    private final String uploadPath = System.getProperty("user.dir") + "/files/";

    // ===================== 通用功能 =====================

    /** 通用文件上传（封面图 / 电子书 / 头像等） */
    @PostMapping("/upload")
    public Result<String> upload(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            return Result.error("500", "上传文件不能为空");
        }
        String originalFilename = file.getOriginalFilename();
        String suffix = FileUtil.getSuffix(originalFilename);
        String fileName = IdUtil.fastSimpleUUID() + "." + suffix;

        File dir = new File(uploadPath);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        file.transferTo(new File(uploadPath + fileName));

        String fileUrl = "http://localhost:8090/files/" + fileName;
        return Result.success(fileUrl);
    }

    // ===================== 管理员书籍管理 =====================

    /** 管理员新增图书（直接公开，status=2） */
    @PostMapping("/add")
    public Result<?> add(@RequestBody SysBook sysBook) {
        if (sysBook.getTitle() == null) {
            return Result.error("500", "书名不能为空");
        }
        sysBook.setStatus(2);
        sysBook.setCreateTime(LocalDateTime.now());
        sysBookService.save(sysBook);
        try { bookSearchService.syncOneBookToEs(sysBook.getId()); } catch (Exception ignored) {}
        return Result.success();
    }

    /** 更新图书 */
    @PutMapping("/update")
    public Result<?> update(@RequestBody SysBook sysBook) {
        sysBookService.updateById(sysBook);
        try { bookSearchService.syncOneBookToEs(sysBook.getId()); } catch (Exception ignored) {}
        return Result.success();
    }

    /** 删除图书 */
    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        sysBookService.removeById(id);
        try { bookSearchService.deleteFromEs(id); } catch (Exception ignored) {}
        return Result.success();
    }

    // ===================== 用户上传书籍 =====================

    /** 用户上传书籍（默认私有 status=0，自动加入用户书架） */
    @PostMapping("/userUpload")
    public Result<?> userUpload(@RequestBody SysBook sysBook) {
        if (sysBook.getTitle() == null || sysBook.getUploaderId() == null) {
            return Result.error("500", "书名和上传者ID不能为空");
        }
        sysBook.setStatus(0);
        sysBook.setCreateTime(LocalDateTime.now());
        sysBookService.save(sysBook);

        // 自动加入上传者的书架
        QueryWrapper<UserBookshelf> check = new QueryWrapper<>();
        check.eq("user_id", sysBook.getUploaderId()).eq("book_id", sysBook.getId());
        if (bookshelfMapper.selectCount(check) == 0) {
            UserBookshelf shelf = new UserBookshelf();
            shelf.setUserId(sysBook.getUploaderId());
            shelf.setBookId(sysBook.getId());
            shelf.setLastReadTime(LocalDateTime.now());
            shelf.setProgressIndex(0);
            shelf.setIsFinished(0);
            shelf.setCurrentChapterIndex(0);
            bookshelfMapper.insert(shelf);
        }

        return Result.success(sysBook);
    }

    /** 用户申请将私有书籍公开（status 0/3 → 1） */
    @PostMapping("/applyPublic/{id}")
    public Result<?> applyPublic(@PathVariable Long id) {
        SysBook book = sysBookService.getById(id);
        if (book == null) return Result.error("404", "书籍不存在");
        if (book.getStatus() != null && book.getStatus() != 0 && book.getStatus() != 3) {
            return Result.error("500", "当前状态不允许申请公开");
        }
        book.setStatus(1);
        sysBookService.updateById(book);
        return Result.success();
    }

    /** 获取用户自己上传的书籍列表 */
    @GetMapping("/myUploads/{userId}")
    public Result<List<SysBook>> getMyUploads(@PathVariable Long userId) {
        return Result.success(sysBookMapper.selectByUploaderId(userId));
    }

    // ===================== 管理员审核 =====================

    /** 获取待审核书籍列表（含上传者昵称） */
    @GetMapping("/pendingReview")
    public Result<List<SysBook>> getPendingReview() {
        return Result.success(sysBookMapper.selectPendingBooks());
    }

    /** 审核书籍（approve=通过, reject=驳回） */
    @PostMapping("/review/{id}")
    public Result<?> reviewBook(@PathVariable Long id, @RequestParam String action) {
        SysBook book = sysBookService.getById(id);
        if (book == null) return Result.error("404", "书籍不存在");

        if ("approve".equals(action)) {
            book.setStatus(2);
            sysBookService.updateById(book);
            try { bookSearchService.syncOneBookToEs(id); } catch (Exception ignored) {}
            return Result.success("审核通过");
        } else if ("reject".equals(action)) {
            book.setStatus(3);
            sysBookService.updateById(book);
            return Result.success("已驳回");
        }
        return Result.error("400", "无效的审核操作");
    }

    // ===================== 公共查询（仅返回已公开书籍） =====================

    /** 获取书籍详情（含平均评分） */
    @GetMapping("/{id}")
    public Result<SysBook> getDetail(@PathVariable Long id) {
        SysBook book = sysBookService.getById(id);
        if (book == null) {
            return Result.error("404", "书籍不存在");
        }
        Double avg = commentMapper.getAvgRating(id);
        book.setAvgRating(avg);
        return Result.success(book);
    }

    /** 热门阅读榜单（仅已公开书籍） */
    @GetMapping("/rank")
    public Result<List<SysBook>> getRankBooks() {
        return Result.success(sysBookMapper.selectRankBooks());
    }

    /** 书籍列表（仅已公开，分页 + 关键词搜索 + 分类筛选） */
    @GetMapping("/list")
    public Result<?> findPage(@RequestParam(defaultValue = "1") Integer pageNum,
                              @RequestParam(defaultValue = "10") Integer pageSize,
                              @RequestParam(defaultValue = "") String keyword,
                              @RequestParam(defaultValue = "") String category,
                              @RequestParam(required = false) Boolean isAdmin) {
        Page<SysBook> page = new Page<>(pageNum, pageSize);
        QueryWrapper<SysBook> query = new QueryWrapper<>();

        // 如果不是管理员，则只返回已公开的书籍（status=2 或 status 为 NULL 的历史数据）
        if (isAdmin == null || !isAdmin) {
            query.and(w -> w.eq("status", 2).or().isNull("status"));
        }

        if (!keyword.isEmpty()) {
            query.and(wrapper -> wrapper.like("title", keyword).or().like("author", keyword));
        }
        if (!category.isEmpty() && !"全部".equals(category)) {
            query.eq("category", category);
        }
        query.orderByDesc("id");
        
        Page<SysBook> resultPage = sysBookService.page(page, query);
        
        // 若为管理员，获取上传者昵称以供展示
        if (isAdmin != null && isAdmin) {
            for (SysBook book : resultPage.getRecords()) {
                if (book.getUploaderId() != null) {
                    SysUser uploader = sysUserService.getById(book.getUploaderId());
                    if (uploader != null) {
                        book.setUploaderNickname(uploader.getNickname());
                    }
                }
            }
        }
        
        return Result.success(resultPage);
    }

    /** 解析书籍章节（自动拆分 TXT 文件为章节结构） */
    @PostMapping("/analyze/{bookId}")
    public Result<?> analyzeBook(@PathVariable Long bookId) {
        SysBook book = sysBookService.getById(bookId);
        if (book == null) return Result.error("404", "书籍不存在");

        QueryWrapper<SysChapter> query = new QueryWrapper<>();
        query.eq("book_id", bookId);
        if (chapterMapper.selectCount(query) > 0) {
            return Result.success("该书已存在章节信息，跳过分析");
        }

        String fileName = book.getFilePath().substring(book.getFilePath().lastIndexOf("/") + 1);
        String finalPath = uploadPath.endsWith("/") || uploadPath.endsWith("\\")
                ? uploadPath + fileName
                : uploadPath + File.separator + fileName;

        File bookFile = new File(finalPath);
        if (!bookFile.exists()) {
            return Result.error("500", "磁盘上找不到文件: " + finalPath);
        }

        List<SysChapter> chapters = ChapterParser.parse(bookId, bookFile);
        for (SysChapter chapter : chapters) {
            chapterMapper.insert(chapter);
        }

        try { bookSearchService.syncOneBookToEs(bookId); } catch (Exception ignored) {}
        return Result.success("成功解析出 " + chapters.size() + " 个章节");
    }

    /** 获取书籍目录列表 */
    @GetMapping("/catalog/{bookId}")
    public Result<List<SysChapter>> getCatalog(@PathVariable Long bookId) {
        return Result.success(chapterMapper.selectCatalog(bookId));
    }

    /** 获取某一章节的详细内容 */
    @GetMapping("/chapter/{chapterId}")
    public Result<SysChapter> getChapterContent(@PathVariable Long chapterId) {
        return Result.success(chapterMapper.selectById(chapterId));
    }

    /** 首页轮播热门书籍（仅已公开） */
    @GetMapping("/hot")
    public Result<List<SysBook>> getHotBooks() {
        return Result.success(sysBookMapper.selectHotBooks());
    }

    /** 随机推荐书籍（仅已公开） */
    @GetMapping("/recommend")
    public Result<List<SysBook>> getRecommendBooks(@RequestParam(required = false) Long userId,
                                                   @RequestParam(defaultValue = "false") boolean refresh) {
        return Result.success(bookRecommendationService.recommendHomeBooks(userId, refresh));
    }
}
