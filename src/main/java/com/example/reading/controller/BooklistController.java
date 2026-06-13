package com.example.reading.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.reading.common.Result;
import com.example.reading.entity.Booklist;
import com.example.reading.entity.BooklistBook;
import com.example.reading.entity.SysBook;
import com.example.reading.entity.UserBookshelf;
import com.example.reading.mapper.BooklistBookMapper;
import com.example.reading.mapper.BooklistMapper;
import com.example.reading.mapper.UserBookshelfMapper;
import com.example.reading.service.AuthContextService;
import com.example.reading.service.ISysBookService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/booklist")
public class BooklistController {

    @Autowired
    private BooklistMapper booklistMapper;

    @Autowired
    private BooklistBookMapper booklistBookMapper;

    @Autowired
    private UserBookshelfMapper shelfMapper;

    @Autowired
    private ISysBookService sysBookService;

    @Autowired
    private AuthContextService authContextService;

    @PostMapping("/create")
    public Result<?> create(@RequestBody Booklist booklist, HttpServletRequest request) {
        Long currentUserId = authContextService.currentUserId(request);
        if (currentUserId == null) return Result.error("403", "Forbidden");
        if (booklist == null) return Result.error("400", "Invalid parameters");
        String name = normalizeText(booklist.getName());
        if (name == null) return Result.error("400", "Booklist name is required");
        if (name.length() > 255) return Result.error("400", "Booklist name is too long");
        booklist.setName(name);
        booklist.setDescription(normalizeText(booklist.getDescription()));
        booklist.setUserId(currentUserId);
        booklist.setShareCode(UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        booklist.setCreateTime(LocalDateTime.now());
        booklistMapper.insert(booklist);
        return Result.success(booklist);
    }

    @GetMapping("/list/{userId}")
    public Result<List<Booklist>> list(@PathVariable Long userId, HttpServletRequest request) {
        if (!authContextService.isSelf(userId, request)) return Result.error("403", "Forbidden");
        QueryWrapper<Booklist> query = new QueryWrapper<>();
        query.eq("user_id", userId).orderByDesc("create_time");
        List<Booklist> lists = booklistMapper.selectList(query);
        for (Booklist bl : lists) bl.setBooks(null);
        return Result.success(lists);
    }

    @DeleteMapping("/delete/{id}")
    public Result<?> delete(@PathVariable Long id, HttpServletRequest request) {
        Booklist booklist = booklistMapper.selectById(id);
        if (booklist == null) return Result.success();
        if (!authContextService.isSelf(booklist.getUserId(), request)) return Result.error("403", "Forbidden");
        booklistMapper.deleteById(id);
        QueryWrapper<BooklistBook> query = new QueryWrapper<>();
        query.eq("booklist_id", id);
        booklistBookMapper.delete(query);
        return Result.success();
    }

    @PostMapping("/addBook")
    public Result<?> addBook(@RequestBody BooklistBook booklistBook, HttpServletRequest request) {
        if (booklistBook == null || booklistBook.getBooklistId() == null || booklistBook.getBookId() == null) {
            return Result.error("400", "Invalid parameters");
        }
        Booklist booklist = booklistMapper.selectById(booklistBook.getBooklistId());
        if (booklist == null) return Result.error("404", "Booklist not found");
        if (!authContextService.isSelf(booklist.getUserId(), request)) return Result.error("403", "Forbidden");
        Long currentUserId = authContextService.currentUserId(request);
        SysBook book = sysBookService.getById(booklistBook.getBookId());
        if (!authContextService.canAccessBook(book, currentUserId)) return Result.error("403", "Forbidden");
        QueryWrapper<BooklistBook> query = new QueryWrapper<>();
        query.eq("booklist_id", booklistBook.getBooklistId()).eq("book_id", booklistBook.getBookId());
        if (booklistBookMapper.selectCount(query) > 0) return Result.error("500", "Book already exists");
        booklistBookMapper.insert(booklistBook);
        return Result.success();
    }

    @DeleteMapping("/removeBook")
    public Result<?> removeBook(@RequestParam Long booklistId,
                                @RequestParam Long bookId,
                                HttpServletRequest request) {
        Booklist booklist = booklistMapper.selectById(booklistId);
        if (booklist == null) return Result.error("404", "Booklist not found");
        if (!authContextService.isSelf(booklist.getUserId(), request)) return Result.error("403", "Forbidden");
        QueryWrapper<BooklistBook> query = new QueryWrapper<>();
        query.eq("booklist_id", booklistId).eq("book_id", bookId);
        booklistBookMapper.delete(query);
        return Result.success();
    }

    @GetMapping("/detail/{id}")
    public Result<Booklist> detail(@PathVariable Long id, HttpServletRequest request) {
        Booklist booklist = booklistMapper.selectById(id);
        if (booklist == null) return Result.error("404", "Booklist not found");
        if (!authContextService.isSelf(booklist.getUserId(), request)) return Result.error("403", "Forbidden");
        booklist.setBooks(booklistMapper.selectBooksByListId(id));
        return Result.success(booklist);
    }

    @GetMapping("/share/{shareCode}")
    public Result<Booklist> getByShareCode(@PathVariable String shareCode) {
        QueryWrapper<Booklist> query = new QueryWrapper<>();
        query.eq("share_code", shareCode);
        Booklist booklist = booklistMapper.selectOne(query);
        if (booklist == null) return Result.error("404", "Booklist not found");
        booklist.setBooks(booklistMapper.selectPublicBooksByListId(booklist.getId()));
        return Result.success(booklist);
    }

    @PostMapping("/import/{shareCode}")
    public Result<?> importBooklist(@PathVariable String shareCode,
                                    @RequestParam(required = false) Long userId,
                                    HttpServletRequest request) {
        Long currentUserId = authContextService.currentUserId(request);
        if (currentUserId == null) return Result.error("403", "Forbidden");

        QueryWrapper<Booklist> query = new QueryWrapper<>();
        query.eq("share_code", shareCode);
        Booklist booklist = booklistMapper.selectOne(query);
        if (booklist == null) return Result.error("404", "Booklist not found");

        QueryWrapper<BooklistBook> bbQuery = new QueryWrapper<>();
        bbQuery.eq("booklist_id", booklist.getId());
        List<BooklistBook> booklistBooks = booklistBookMapper.selectList(bbQuery);

        Set<Long> bookIds = new LinkedHashSet<>();
        for (BooklistBook bb : booklistBooks) {
            if (bb.getBookId() != null) {
                bookIds.add(bb.getBookId());
            }
        }

        Map<Long, SysBook> bookMap = new HashMap<>();
        if (!bookIds.isEmpty()) {
            for (SysBook book : sysBookService.listByIds(bookIds)) {
                bookMap.put(book.getId(), book);
            }
        }

        Set<Long> existingShelfBookIds = new HashSet<>();
        if (!bookIds.isEmpty()) {
            QueryWrapper<UserBookshelf> shelfQuery = new QueryWrapper<>();
            shelfQuery.eq("user_id", currentUserId).in("book_id", bookIds);
            for (UserBookshelf shelf : shelfMapper.selectList(shelfQuery)) {
                existingShelfBookIds.add(shelf.getBookId());
            }
        }

        int added = 0;
        for (BooklistBook bb : booklistBooks) {
            SysBook book = bookMap.get(bb.getBookId());
            if (!authContextService.isPublicBook(book)) {
                continue;
            }
            if (!existingShelfBookIds.contains(bb.getBookId())) {
                UserBookshelf shelf = new UserBookshelf();
                shelf.setUserId(currentUserId);
                shelf.setBookId(bb.getBookId());
                shelf.setLastReadTime(LocalDateTime.now());
                shelf.setProgressIndex(0);
                shelf.setIsFinished(0);
                shelf.setCurrentChapterIndex(0);
                shelfMapper.insert(shelf);
                existingShelfBookIds.add(bb.getBookId());
                added++;
            }
        }
        return Result.success("Imported " + added + " books");
    }

    private String normalizeText(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
