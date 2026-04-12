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
import java.util.UUID;

/**
 * 书单管理控制器
 * 提供书单的创建、删除、详情查询、书籍增删、分享码访问及一键导入到书架功能。
 */
@RestController
@RequestMapping("/booklist")
public class BooklistController {

    @Autowired
    private BooklistMapper booklistMapper;

    @Autowired
    private BooklistBookMapper booklistBookMapper;

    @Autowired
    private UserBookshelfMapper shelfMapper;

    /** 创建书单（自动生成 8 位分享码） */
    @PostMapping("/create")
    public Result<?> create(@RequestBody Booklist booklist) {
        booklist.setShareCode(UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        booklist.setCreateTime(LocalDateTime.now());
        booklistMapper.insert(booklist);
        return Result.success(booklist);
    }

    /** 获取用户的所有书单 */
    @GetMapping("/list/{userId}")
    public Result<List<Booklist>> list(@PathVariable Long userId) {
        QueryWrapper<Booklist> query = new QueryWrapper<>();
        query.eq("user_id", userId).orderByDesc("create_time");
        List<Booklist> lists = booklistMapper.selectList(query);

        for (Booklist bl : lists) {
            bl.setBooks(null);
        }

        return Result.success(lists);
    }

    /** 删除书单（同时清除关联的书籍关系） */
    @DeleteMapping("/delete/{id}")
    public Result<?> delete(@PathVariable Long id) {
        booklistMapper.deleteById(id);
        QueryWrapper<BooklistBook> query = new QueryWrapper<>();
        query.eq("booklist_id", id);
        booklistBookMapper.delete(query);
        return Result.success();
    }

    /** 向书单添加一本书（自动去重） */
    @PostMapping("/addBook")
    public Result<?> addBook(@RequestBody BooklistBook booklistBook) {
        QueryWrapper<BooklistBook> query = new QueryWrapper<>();
        query.eq("booklist_id", booklistBook.getBooklistId())
                .eq("book_id", booklistBook.getBookId());
        if (booklistBookMapper.selectCount(query) > 0) {
            return Result.error("500", "该书已在书单中");
        }
        booklistBookMapper.insert(booklistBook);
        return Result.success();
    }

    /** 从书单移除一本书 */
    @DeleteMapping("/removeBook")
    public Result<?> removeBook(@RequestParam Long booklistId, @RequestParam Long bookId) {
        QueryWrapper<BooklistBook> query = new QueryWrapper<>();
        query.eq("booklist_id", booklistId).eq("book_id", bookId);
        booklistBookMapper.delete(query);
        return Result.success();
    }

    /** 获取书单详情（含完整书籍列表） */
    @GetMapping("/detail/{id}")
    public Result<Booklist> detail(@PathVariable Long id) {
        Booklist booklist = booklistMapper.selectById(id);
        if (booklist == null) {
            return Result.error("404", "书单不存在");
        }
        booklist.setBooks(booklistMapper.selectBooksByListId(id));
        return Result.success(booklist);
    }

    /** 通过分享码获取书单内容（公开接口） */
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

    /** 通过分享码一键导入书单到用户书架（已在书架中的自动跳过） */
    @PostMapping("/import/{shareCode}")
    public Result<?> importBooklist(@PathVariable String shareCode, @RequestParam Long userId) {
        QueryWrapper<Booklist> query = new QueryWrapper<>();
        query.eq("share_code", shareCode);
        Booklist booklist = booklistMapper.selectOne(query);
        if (booklist == null) {
            return Result.error("404", "书单不存在");
        }

        QueryWrapper<BooklistBook> bbQuery = new QueryWrapper<>();
        bbQuery.eq("booklist_id", booklist.getId());
        List<BooklistBook> booklistBooks = booklistBookMapper.selectList(bbQuery);

        int added = 0;
        for (BooklistBook bb : booklistBooks) {
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
