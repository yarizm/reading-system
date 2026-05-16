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
import com.example.reading.entity.BookReviewRequest;
import com.example.reading.mapper.BookReviewRequestMapper;
import com.example.reading.dto.BookEditDTO;
import com.example.reading.dto.ReviewActionDTO;
import com.example.reading.service.AuthContextService;
import com.example.reading.service.DifyKnowledgeBaseService;
import com.example.reading.service.IBookRecommendationService;
import com.example.reading.service.ISysBookService;
import com.example.reading.service.ISysUserService;
import com.google.gson.Gson;
import com.example.reading.utils.ChapterParser;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;

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

    private static final Logger log = LoggerFactory.getLogger(SysBookController.class);

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

    @Autowired
    private BookReviewRequestMapper reviewRequestMapper;

    @Autowired
    private DifyKnowledgeBaseService difyKnowledgeBaseService;

    private final Gson gson = new Gson();

    @Autowired
    private AuthContextService authContextService;

    @Value("${file.upload-path}")
    private String uploadPath;

    private boolean isUploader(SysBook book, HttpServletRequest request) {
        Long uid = authContextService.currentUserId(request);
        return book != null && uid != null
                && book.getUploaderId() != null
                && book.getUploaderId().equals(uid);
    }

    private SysBook sanitizeBookForPublic(SysBook book) {
        if (book != null) {
            book.setFilePath(null);
        }
        return book;
    }

    private List<SysBook> sanitizeBooksForPublic(List<SysBook> books) {
        books.forEach(this::sanitizeBookForPublic);
        return books;
    }

    private void trySyncToEs(Long bookId) {
        try { bookSearchService.syncOneBookToEs(bookId); }
        catch (Exception e) { log.error("Failed to sync book to ES. bookId={}", bookId, e); }
    }

    private void tryDeleteFromEs(Long bookId) {
        try { bookSearchService.deleteFromEs(bookId); }
        catch (Exception e) { log.error("Failed to delete book from ES. bookId={}", bookId, e); }
    }

    private void trySyncToKb(Long bookId) {
        try { difyKnowledgeBaseService.syncOneBookToKb(bookId); }
        catch (Exception e) { log.error("Failed to sync book to KB. bookId={}", bookId, e); }
    }

    private void tryDeleteFromKb(Long bookId) {
        try { difyKnowledgeBaseService.deleteFromKb(bookId); }
        catch (Exception e) { log.error("Failed to delete book from KB. bookId={}", bookId, e); }
    }

    // ===================== 通用功能 =====================

    /** 通用文件上传（封面图 / 电子书 / 头像等） */
    @PostMapping("/upload")
    public Result<String> upload(@RequestParam("file") MultipartFile file,
                                 HttpServletRequest request) throws IOException {
        if (authContextService.currentUserId(request) == null) {
            return Result.error("403", "Forbidden");
        }
        if (file.isEmpty()) {
            return Result.error("500", "上传文件不能为空");
        }
        String originalFilename = file.getOriginalFilename();
        String suffix = FileUtil.getSuffix(originalFilename);
        if (suffix == null || !Set.of("jpg", "jpeg", "png", "webp", "gif", "txt").contains(suffix.toLowerCase())) {
            return Result.error("400", "Unsupported file type");
        }
        String fileName = IdUtil.fastSimpleUUID() + "." + suffix;

        File dir = new File(uploadPath);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        file.transferTo(new File(dir, fileName));

        String fileUrl = "/files/" + fileName;
        return Result.success(fileUrl);
    }

    // ===================== 管理员书籍管理 =====================

    /** 管理员新增图书（直接公开，status=2） */
    @Transactional
    @PostMapping("/add")
    public Result<?> add(@RequestBody SysBook sysBook, HttpServletRequest request) {
        if (!authContextService.isAdmin(request)) {
            return Result.error("403", "Forbidden");
        }
        if (sysBook.getTitle() == null) {
            return Result.error("500", "书名不能为空");
        }
        sysBook.setStatus(2);
        sysBook.setCreateTime(LocalDateTime.now());
        sysBookService.save(sysBook);
        trySyncToEs(sysBook.getId());
        trySyncToKb(sysBook.getId());
        return Result.success();
    }

    /** 更新图书 */
    @Transactional
    @PutMapping("/update")
    public Result<?> update(@RequestBody SysBook sysBook, HttpServletRequest request) {
        if (!authContextService.isAdmin(request)) {
            return Result.error("403", "Forbidden");
        }
        sysBookService.updateById(sysBook);
        trySyncToEs(sysBook.getId());
        trySyncToKb(sysBook.getId());
        return Result.success();
    }

    /** 删除图书 */
    @Transactional
    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id, HttpServletRequest request) {
        if (!authContextService.isAdmin(request)) {
            return Result.error("403", "Forbidden");
        }
        sysBookService.removeById(id);
        tryDeleteFromEs(id);
        tryDeleteFromKb(id);
        return Result.success();
    }

    // ===================== 用户上传书籍 =====================

    /** 用户上传书籍（默认私有 status=0，自动加入用户书架） */
    @PostMapping("/userUpload")
    public Result<?> userUpload(@RequestBody SysBook sysBook, HttpServletRequest request) {
        Long currentUserId = authContextService.currentUserId(request);
        if (currentUserId == null || !currentUserId.equals(sysBook.getUploaderId())) {
            return Result.error("403", "Forbidden");
        }
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
    @Transactional
    @PostMapping("/applyPublic/{id}")
    public Result<?> applyPublic(@PathVariable Long id,
                                              HttpServletRequest request) {
        SysBook book = sysBookService.getById(id);
        if (book == null) return Result.error("404", "书籍不存在");
        if (!isUploader(book, request)) {
            return Result.error("403", "无权操作该书籍");
        }
        if (book.getStatus() != null && book.getStatus() != 0 && book.getStatus() != 3) {
            return Result.error("500", "当前状态不允许申请公开");
        }

        // 检查是否已有待审核的请求
        List<BookReviewRequest> pending = reviewRequestMapper.selectPendingByBookId(id);
        if (!pending.isEmpty()) {
            return Result.error("500", "该书籍已有待审核的请求，请等待审核完成");
        }

        book.setStatus(1);
        sysBookService.updateById(book);

        // 创建新书审核请求记录
        BookReviewRequest reviewRequest = new BookReviewRequest();
        reviewRequest.setBookId(id);
        reviewRequest.setUserId(book.getUploaderId());
        reviewRequest.setRequestType("new");
        reviewRequest.setStatus(0);
        reviewRequest.setCreateTime(LocalDateTime.now());
        reviewRequestMapper.insert(reviewRequest);

        return Result.success();
    }

    /** 获取用户自己上传的书籍列表 */
    @GetMapping("/myUploads/{userId}")
    public Result<List<SysBook>> getMyUploads(@PathVariable Long userId, HttpServletRequest request) {
        if (!authContextService.isSelfOrAdmin(userId, request)) {
            return Result.error("403", "Forbidden");
        }
        return Result.success(sysBookMapper.selectByUploaderId(userId));
    }

    // ===================== 用户书籍管理 =====================

    /** 用户编辑私有/驳回书籍（直接更新 sys_book，status 保持原状态） */
    @PutMapping("/userEdit")
    public Result<?> userEdit(@RequestBody BookEditDTO dto, HttpServletRequest request) {
        if (dto.getId() == null) return Result.error("400", "书籍ID不能为空");
        SysBook existing = sysBookService.getById(dto.getId());
        if (existing == null) return Result.error("404", "书籍不存在");
        if (!isUploader(existing, request)) {
            return Result.error("403", "无权操作该书籍");
        }
        if (existing.getStatus() != null && existing.getStatus() != 0 && existing.getStatus() != 1 && existing.getStatus() != 3 && existing.getStatus() != 4) {
            return Result.error("500", "当前状态不允许直接编辑");
        }
        if (dto.getTitle() != null) existing.setTitle(dto.getTitle());
        if (dto.getAuthor() != null) existing.setAuthor(dto.getAuthor());
        if (dto.getCategory() != null) existing.setCategory(dto.getCategory());
        if (dto.getDescription() != null) existing.setDescription(dto.getDescription());
        if (dto.getTags() != null) existing.setTags(dto.getTags());
        if (dto.getCoverUrl() != null) existing.setCoverUrl(dto.getCoverUrl());
        if (dto.getFilePath() != null) existing.setFilePath(dto.getFilePath());
        sysBookService.updateById(existing);
        return Result.success();
    }

    /** 用户提交已上线书籍的编辑审核（创建 edit 类型 review request） */
    @PostMapping("/applyEdit")
    @Transactional
    public Result<?> applyEdit(@RequestBody SysBook sysBook,
                                            HttpServletRequest request) {
        if (sysBook.getId() == null) return Result.error("400", "书籍ID不能为空");
        SysBook existing = sysBookService.getById(sysBook.getId());
        if (existing == null) return Result.error("404", "书籍不存在");
        if (!isUploader(existing, request)) {
            return Result.error("403", "无权操作该书籍");
        }
        if (existing.getStatus() == null || existing.getStatus() != 2) {
            return Result.error("500", "只有已上线的书籍才能提交编辑审核");
        }

        // 检查是否已有待审核的请求
        List<BookReviewRequest> pending = reviewRequestMapper.selectPendingByBookId(sysBook.getId());
        if (!pending.isEmpty()) {
            return Result.error("500", "该书籍已有待审核的请求，请等待审核完成");
        }

        BookReviewRequest reviewRequest = new BookReviewRequest();
        reviewRequest.setBookId(sysBook.getId());
        reviewRequest.setUserId(existing.getUploaderId());
        reviewRequest.setRequestType("edit");
        reviewRequest.setStatus(0);
        reviewRequest.setCreateTime(LocalDateTime.now());

        // 将变更后的书籍信息序列化为 JSON
        SysBook snapshot = new SysBook();
        snapshot.setTitle(sysBook.getTitle());
        snapshot.setAuthor(sysBook.getAuthor());
        snapshot.setDescription(sysBook.getDescription());
        snapshot.setCategory(sysBook.getCategory());
        snapshot.setTags(sysBook.getTags());
        snapshot.setCoverUrl(sysBook.getCoverUrl());
        snapshot.setFilePath(sysBook.getFilePath());
        reviewRequest.setNewBookData(gson.toJson(snapshot));

        reviewRequestMapper.insert(reviewRequest);
        return Result.success("编辑审核已提交");
    }

    /** 用户申请下架已上线书籍（创建 delist 类型 review request） */
    @PostMapping("/applyDelist/{id}")
    @Transactional
    public Result<?> applyDelist(@PathVariable Long id,
                                              HttpServletRequest request) {
        SysBook book = sysBookService.getById(id);
        if (book == null) return Result.error("404", "书籍不存在");
        if (!isUploader(book, request)) {
            return Result.error("403", "无权操作该书籍");
        }
        if (book.getStatus() == null || book.getStatus() != 2) {
            return Result.error("500", "只有已上线的书籍才能申请下架");
        }

        List<BookReviewRequest> pending = reviewRequestMapper.selectPendingByBookId(id);
        if (!pending.isEmpty()) {
            return Result.error("500", "该书籍已有待审核的请求，请等待审核完成");
        }

        BookReviewRequest reviewRequest = new BookReviewRequest();
        reviewRequest.setBookId(id);
        reviewRequest.setUserId(book.getUploaderId());
        reviewRequest.setRequestType("delist");
        reviewRequest.setStatus(0);
        reviewRequest.setCreateTime(LocalDateTime.now());
        reviewRequestMapper.insert(reviewRequest);
        return Result.success("下架审核已提交");
    }

    /** 用户查看自己的所有审核请求 */
    @GetMapping("/reviewRequests/{userId}")
    public Result<List<BookReviewRequest>> getUserReviewRequests(@PathVariable Long userId,
                                                                 HttpServletRequest request) {
        if (!authContextService.isSelfOrAdmin(userId, request)) {
            return Result.error("403", "Forbidden");
        }
        return Result.success(reviewRequestMapper.selectByUserId(userId));
    }

    /** 管理员查看所有待审核请求 */
    @GetMapping("/reviewRequests/pending")
    public Result<List<BookReviewRequest>> getPendingReviewRequests(HttpServletRequest request) {
        if (!authContextService.isAdmin(request)) {
            return Result.error("403", "无权查看审核请求");
        }
        return Result.success(reviewRequestMapper.selectPendingAll());
    }

    /** 管理员审核单个请求 */
    @Transactional
    @PostMapping("/reviewRequest/{id}")
    public Result<?> reviewRequest(@PathVariable Long id,
                                   @RequestBody ReviewActionDTO dto,
                                   HttpServletRequest httpRequest) {
        if (!authContextService.isAdmin(httpRequest)) {
            return Result.error("403", "无权审核请求");
        }
        String action = dto.getAction();
        String rejectReason = dto.getRejectReason();
        BookReviewRequest request = reviewRequestMapper.selectById(id);
        if (request == null) return Result.error("404", "审核请求不存在");
        if (request.getStatus() != 0) return Result.error("500", "该请求已处理");

        SysBook book = sysBookService.getById(request.getBookId());
        if (book == null) return Result.error("404", "关联书籍不存在");

        if ("approve".equals(action)) {
            switch (request.getRequestType()) {
                case "new" -> {
                    book.setStatus(2);
                    sysBookService.updateById(book);
                    trySyncToEs(book.getId());
                    trySyncToKb(book.getId());
                }
                case "edit" -> {
                    SysBook changes = gson.fromJson(request.getNewBookData(), SysBook.class);
                    if (changes.getTitle() != null) book.setTitle(changes.getTitle());
                    if (changes.getAuthor() != null) book.setAuthor(changes.getAuthor());
                    if (changes.getDescription() != null) book.setDescription(changes.getDescription());
                    if (changes.getCategory() != null) book.setCategory(changes.getCategory());
                    if (changes.getTags() != null) book.setTags(changes.getTags());
                    if (changes.getCoverUrl() != null) book.setCoverUrl(changes.getCoverUrl());
                    if (changes.getFilePath() != null) book.setFilePath(changes.getFilePath());
                    sysBookService.updateById(book);
                    trySyncToEs(book.getId());
                    trySyncToKb(book.getId());
                }
                case "delist" -> {
                    book.setStatus(4);
                    sysBookService.updateById(book);
                    tryDeleteFromEs(book.getId());
                    tryDeleteFromKb(book.getId());
                }
                default -> {
                    return Result.error("400", "不支持的审核请求类型");
                }
            }
            request.setStatus(1);
            request.setReviewTime(LocalDateTime.now());
            reviewRequestMapper.updateById(request);
            return Result.success("审核通过");
        } else if ("reject".equals(action)) {
            if (rejectReason == null || rejectReason.isBlank()) {
                return Result.error("400", "驳回原因不能为空");
            }
            request.setStatus(2);
            request.setRejectReason(rejectReason);
            request.setReviewTime(LocalDateTime.now());
            reviewRequestMapper.updateById(request);

            // 新书审核驳回时，将书籍状态改为已驳回
            if ("new".equals(request.getRequestType())) {
                book.setStatus(3);
                sysBookService.updateById(book);
            }
            return Result.success("已驳回");
        }
        return Result.error("400", "无效的审核操作");
    }

    // ===================== 公共查询（仅返回已公开书籍） =====================

    /** 获取书籍详情（含平均评分） */
    @GetMapping("/{id}")
    public Result<SysBook> getDetail(@PathVariable Long id, HttpServletRequest request) {
        SysBook book = sysBookService.getById(id);
        if (book == null) {
            return Result.error("404", "书籍不存在");
        }
        if (!authContextService.canViewBook(book, request)) {
            return Result.error("403", "Forbidden");
        }
        Double avg = commentMapper.getAvgRating(id);
        book.setAvgRating(avg);
        if (!isUploader(book, request) && !authContextService.isAdmin(request)) {
            sanitizeBookForPublic(book);
        }
        return Result.success(book);
    }

    /** 热门阅读榜单（仅已公开书籍） */
    @GetMapping("/rank")
    public Result<List<SysBook>> getRankBooks() {
        return Result.success(sanitizeBooksForPublic(sysBookMapper.selectRankBooks()));
    }

    /** 书籍列表（仅已公开，分页 + 关键词搜索 + 分类筛选） */
    @GetMapping("/list")
    public Result<?> findPage(@RequestParam(defaultValue = "1") Integer pageNum,
                              @RequestParam(defaultValue = "10") Integer pageSize,
                              @RequestParam(defaultValue = "") String keyword,
                              @RequestParam(defaultValue = "") String category,
                              @RequestParam(required = false) Boolean isAdmin,
                              HttpServletRequest request) {
        boolean adminView = Boolean.TRUE.equals(isAdmin);
        if (adminView && !authContextService.isAdmin(request)) {
            return Result.error("403", "Forbidden");
        }
        Page<SysBook> page = new Page<>(pageNum, pageSize);
        QueryWrapper<SysBook> query = new QueryWrapper<>();

        // 如果不是管理员，则只返回已公开的书籍（status=2 或 status 为 NULL 的历史数据）
        if (!adminView) {
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
        if (adminView) {
            for (SysBook book : resultPage.getRecords()) {
                if (book.getUploaderId() != null) {
                    SysUser uploader = sysUserService.getById(book.getUploaderId());
                    if (uploader != null) {
                        book.setUploaderNickname(uploader.getNickname());
                    }
                }
            }
        }
        
        if (!adminView) {
            sanitizeBooksForPublic(resultPage.getRecords());
        }
        return Result.success(resultPage);
    }

    /** 解析书籍章节（自动拆分 TXT 文件为章节结构） */
    @PostMapping("/analyze/{bookId}")
    public Result<?> analyzeBook(@PathVariable Long bookId,
                                 HttpServletRequest request) {
        Long currentUserId = authContextService.currentUserId(request);
        if (currentUserId == null) {
            return Result.error("403", "Forbidden");
        }
        SysBook book = sysBookService.getById(bookId);
        if (book == null) return Result.error("404", "书籍不存在");

        if (!authContextService.canViewBook(book, currentUserId)) {
            return Result.error("403", "Forbidden");
        }

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

        trySyncToEs(bookId);
        trySyncToKb(bookId);
        return Result.success("成功解析出 " + chapters.size() + " 个章节");
    }

    /** 获取书籍目录列表 */
    @GetMapping("/catalog/{bookId}")
    public Result<List<SysChapter>> getCatalog(@PathVariable Long bookId, HttpServletRequest request) {
        SysBook book = sysBookService.getById(bookId);
        if (book == null) return Result.error("404", "Book not found");
        if (!authContextService.canViewBook(book, request)) return Result.error("403", "Forbidden");
        return Result.success(chapterMapper.selectCatalog(bookId));
    }

    /** 获取某一章节的详细内容 */
    @GetMapping("/chapter/{chapterId}")
    public Result<SysChapter> getChapterContent(@PathVariable Long chapterId, HttpServletRequest request) {
        SysChapter chapter = chapterMapper.selectById(chapterId);
        if (chapter == null) return Result.error("404", "Chapter not found");
        SysBook book = sysBookService.getById(chapter.getBookId());
        if (book == null) return Result.error("404", "Book not found");
        if (!authContextService.canViewBook(book, request)) return Result.error("403", "Forbidden");
        return Result.success(chapter);
    }

    /** 首页轮播热门书籍（仅已公开） */
    @GetMapping("/hot")
    public Result<List<SysBook>> getHotBooks() {
        return Result.success(sanitizeBooksForPublic(sysBookMapper.selectHotBooks()));
    }

    /** 随机推荐书籍（仅已公开） */
    @GetMapping("/recommend")
    public Result<List<SysBook>> getRecommendBooks(@RequestParam(required = false) Long userId,
                                                   @RequestParam(defaultValue = "false") boolean refresh,
                                                   HttpServletRequest request) {
        Long currentUserId = authContextService.currentUserId(request);
        Long effectiveUserId = currentUserId;
        if (userId != null && !userId.equals(currentUserId)) {
            effectiveUserId = null;
        }
        return Result.success(sanitizeBooksForPublic(bookRecommendationService.recommendHomeBooks(effectiveUserId, refresh)));
    }
}
