package com.example.reading.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.reading.common.Result;
import com.example.reading.entity.Booklist;
import com.example.reading.entity.BooklistBook;
import com.example.reading.entity.UserBookshelf;
import com.example.reading.mapper.BooklistBookMapper;
import com.example.reading.mapper.BooklistMapper;
import com.example.reading.mapper.UserBookshelfMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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

    /**
     * 创建书单
     */
    @PostMapping("/create")
    public Result<?> create(@RequestBody Booklist booklist) {
        booklist.setShareCode(UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        booklist.setCreateTime(LocalDateTime.now());
        booklistMapper.insert(booklist);
        return Result.success(booklist);
    }

    /**
     * 获取用户的所有书单
     */
    @GetMapping("/list/{userId}")
    public Result<List<Booklist>> list(@PathVariable Long userId) {
        QueryWrapper<Booklist> query = new QueryWrapper<>();
        query.eq("user_id", userId).orderByDesc("create_time");
        List<Booklist> lists = booklistMapper.selectList(query);

        // 为每个书单填充书籍数量
        for (Booklist bl : lists) {
            QueryWrapper<BooklistBook> countQuery = new QueryWrapper<>();
            countQuery.eq("booklist_id", bl.getId());
            long count = booklistBookMapper.selectCount(countQuery);
            // 复用 books 字段传递 count，前端通过 books.length 或单独字段获取
            bl.setBooks(null); // 列表页不需要完整书籍信息
        }

        return Result.success(lists);
    }

    /**
     * 删除书单
     */
    @DeleteMapping("/delete/{id}")
    public Result<?> delete(@PathVariable Long id) {
        booklistMapper.deleteById(id);
        // 同时删除关联关系
        QueryWrapper<BooklistBook> query = new QueryWrapper<>();
        query.eq("booklist_id", id);
        booklistBookMapper.delete(query);
        return Result.success();
    }

    /**
     * 向书单添加一本书
     */
    @PostMapping("/addBook")
    public Result<?> addBook(@RequestBody BooklistBook booklistBook) {
        // 检查是否已存在
        QueryWrapper<BooklistBook> query = new QueryWrapper<>();
        query.eq("booklist_id", booklistBook.getBooklistId())
                .eq("book_id", booklistBook.getBookId());
        if (booklistBookMapper.selectCount(query) > 0) {
            return Result.error("500", "该书已在书单中");
        }
        booklistBookMapper.insert(booklistBook);
        return Result.success();
    }

    /**
     * 从书单移除一本书
     */
    @DeleteMapping("/removeBook")
    public Result<?> removeBook(@RequestParam Long booklistId, @RequestParam Long bookId) {
        QueryWrapper<BooklistBook> query = new QueryWrapper<>();
        query.eq("booklist_id", booklistId).eq("book_id", bookId);
        booklistBookMapper.delete(query);
        return Result.success();
    }

    /**
     * 获取书单详情（包含书籍列表）
     */
    @GetMapping("/detail/{id}")
    public Result<Booklist> detail(@PathVariable Long id) {
        Booklist booklist = booklistMapper.selectById(id);
        if (booklist == null) {
            return Result.error("404", "书单不存在");
        }
        booklist.setBooks(booklistMapper.selectBooksByListId(id));
        return Result.success(booklist);
    }

    /**
     * 通过分享码获取书单内容（公开接口）
     */
    @GetMapping("/share/{shareCode}")
    public Result<Booklist> getByShareCode(@PathVariable String shareCode) {
        QueryWrapper<Booklist> query = new QueryWrapper<>();
        query.eq("share_code", shareCode);
        Booklist booklist = booklistMapper.selectOne(query);
        if (booklist == null) {
            return Result.error("404", "书单不存在或链接已失效");
        }
        booklist.setBooks(booklistMapper.selectBooksByListId(booklist.getId()));
        return Result.success(booklist);
    }

    /**
     * 一键导入书单到书架
     */
    @PostMapping("/import/{shareCode}")
    public Result<?> importBooklist(@PathVariable String shareCode, @RequestParam Long userId) {
        // 1. 找到书单
        QueryWrapper<Booklist> query = new QueryWrapper<>();
        query.eq("share_code", shareCode);
        Booklist booklist = booklistMapper.selectOne(query);
        if (booklist == null) {
            return Result.error("404", "书单不存在");
        }

        // 2. 获取书单中的所有书籍ID
        QueryWrapper<BooklistBook> bbQuery = new QueryWrapper<>();
        bbQuery.eq("booklist_id", booklist.getId());
        List<BooklistBook> booklistBooks = booklistBookMapper.selectList(bbQuery);

        int added = 0;
        for (BooklistBook bb : booklistBooks) {
            // 检查是否已在书架中
            QueryWrapper<UserBookshelf> shelfQuery = new QueryWrapper<>();
            shelfQuery.eq("user_id", userId).eq("book_id", bb.getBookId());
            if (shelfMapper.selectCount(shelfQuery) == 0) {
                UserBookshelf shelf = new UserBookshelf();
                shelf.setUserId(userId);
                shelf.setBookId(bb.getBookId());
                shelf.setLastReadTime(LocalDateTime.now());
                shelf.setProgressIndex(0);
                shelf.setIsFinished(0);
                shelf.setCurrentChapterIndex(0);
                shelfMapper.insert(shelf);
                added++;
            }
        }

        return Result.success("成功导入 " + added + " 本书到书架");
    }
}
