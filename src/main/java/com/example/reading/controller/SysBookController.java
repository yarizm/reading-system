package com.example.reading.controller;
import java.nio.charset.StandardCharsets;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.Role;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.reading.common.Result;
import com.example.reading.entity.SysBook;
import com.example.reading.entity.SysChapter;
import com.example.reading.service.ISysBookService;
import com.example.reading.utils.ChapterParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.concurrent.TimeUnit;
import java.io.File;
import java.io.IOException;
import java.util.*;

@RestController
@RequestMapping("/sysBook")
public class SysBookController {

    @Autowired
    private ISysBookService sysBookService;

    // 注入 CommentMapper
    @Autowired
    private com.example.reading.mapper.SysCommentMapper commentMapper;

    @Autowired
    private com.example.reading.mapper.SysChapterMapper chapterMapper;

    @Autowired
    private com.example.reading.mapper.SysBookMapper sysBookMapper;

    @Autowired
    private com.example.reading.mapper.UserBookshelfMapper bookshelfMapper; // 注入书架 Mapper

    @Value("${file.upload-path}")
    // 定义文件上传的根路径 (项目根目录下的 files 文件夹)
    private final String uploadPath = System.getProperty("user.dir") + "/files/";

    // === 新增：注入 Redis 模板 ===
    @Autowired
    private StringRedisTemplate redisTemplate;


    // 复用 AI Controller 的配置
    @Value("${ai.dashscope.api-key}")
    private String apiKey;

    // === 新增：读取配置文件中的开关 (默认 false) ===
    @Value("${ai.enable-recommendation:false}")
    private boolean isAiRecommendationEnabled;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * [管理员] 文件上传接口 (封面图 或 电子书文件)
     */
    @PostMapping("/upload")
    public Result<String> upload(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            return Result.error("500", "上传文件不能为空");
        }

        // 1. 获取原文件名和后缀
        String originalFilename = file.getOriginalFilename();
        String suffix = FileUtil.getSuffix(originalFilename); // 需要引入 Hutool 或自己截取字符串

        // 2. 生成唯一文件名 (防止重名覆盖)
        String fileName = IdUtil.fastSimpleUUID() + "." + suffix;

        // 3. 确保目录存在
        File dir = new File(uploadPath);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        // 4. 保存文件到本地
        file.transferTo(new File(uploadPath + fileName));

        // 5. 返回可访问的 URL 地址
        // 假设后端端口是 8090，这里返回相对路径，前端拼上前缀或者直接返回绝对路径
        String fileUrl = "http://localhost:8090/files/" + fileName;
        return Result.success(fileUrl);
    }

    /**
     * [管理员] 新增图书
     */
    @PostMapping("/add")
    public Result<?> add(@RequestBody SysBook sysBook) {
        if (sysBook.getTitle() == null) {
            return Result.error("500", "书名不能为空");
        }
        sysBookService.save(sysBook);
        return Result.success();
    }

    /**
     * [管理员] 更新图书
     */
    @PutMapping("/update")
    public Result<?> update(@RequestBody SysBook sysBook) {
        sysBookService.updateById(sysBook);
        return Result.success();
    }

    /**
     * [管理员] 删除图书
     */
    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        // 1. 删除数据库记录
        sysBookService.removeById(id);

        // 2. (可选) 删除关联的笔记、书架记录、以及本地的物理文件
        // 实际生产中物理文件一般不删，或者由定时任务清理
        return Result.success();
    }

    /**
     * 查询所有书籍列表
     */
//    @GetMapping("/list")
//    public Result<List<SysBook>> list(@RequestParam(defaultValue = "") String title) {
//        QueryWrapper<SysBook> queryWrapper = new QueryWrapper<>();
//        if (StrUtil.isNotBlank(title)) {
//            queryWrapper.like("title", title);
//        }
//        // 按创建时间倒序排
//        queryWrapper.orderByDesc("create_time");
//        return Result.success(sysBookService.list(queryWrapper));
//    }

    /**
     * 获取书籍详情
     */
    @GetMapping("/{id}")
    public Result<SysBook> getDetail(@PathVariable Long id) {
        SysBook book = sysBookService.getById(id);
        if (book == null) {
            return Result.error("404", "书籍不存在");
        }

        // === 关键逻辑：查询并设置平均分 ===
        Double avg = commentMapper.getAvgRating(id);

        // 如果没人评分，默认给 5.0 分或者 0 分，这里看你策略，通常给 0 或者 5
        // 这里给 0，前端会显示空星星
        book.setAvgRating(avg);

        return Result.success(book);
    }
    /**
     * 读取书籍内容 (目前仅支持 TXT)
     * 为了防止过大，这里可以做个限制，比如只读前 50000 字，或者做分页
     */
    @GetMapping("/content/{id}")
    public Result<String> getContent(@PathVariable Long id) {
        SysBook book = sysBookService.getById(id);
        if (book == null) {
            return Result.error("404", "书籍不存在");
        }

        // 1. 获取数据库存的路径 (这是个 URL，如 http://localhost:8090/files/abc.txt)
        String fileUrl = book.getFilePath();
        if (StrUtil.isBlank(fileUrl)) {
            return Result.error("404", "书籍文件路径为空");
        }

        // 2. 关键修复：从 URL 中提取文件名，拼接到本地磁盘路径
        // 例如：从 http://.../files/123_test.txt 提取出 123_test.txt
        String fileName = FileUtil.getName(fileUrl);

        // 3. 拼接真实本地路径：D:/WORK/reading-system-files/123_test.txt
        String localPath = uploadPath + fileName;

        // 4. 检查文件是否存在
        if (!FileUtil.exist(localPath)) {
            // 调试日志：方便你看看到底去哪找文件了
            System.out.println("找不到文件: " + localPath);
            return Result.error("404", "书籍文件丢失，请检查磁盘路径");
        }

        // 5. 简单判断后缀
        if (!localPath.toLowerCase().endsWith(".txt")) {
            return Result.error("500", "演示版本仅支持 TXT 格式");
        }

        try {
            // 6. 读取内容
            String content = FileUtil.readString(localPath, StandardCharsets.UTF_8);
            if (content.length() > 20000) {
                content = content.substring(0, 20000) + "\n\n......(内容过长，仅展示前2万字)......";
            }
            return Result.success(content);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("500", "读取文件失败: " + e.getMessage());
        }
    }

    /**
     * 2. 首页-热门阅读榜单 (取浏览量最高的 10 本)
     */
    @GetMapping("/rank")
    public Result<List<SysBook>> getRankBooks() {
        QueryWrapper<SysBook> query = new QueryWrapper<>();
        // 实际项目中应按 view_count 排序
        query.orderByDesc("id");
        query.last("limit 10");
        return Result.success(sysBookService.list(query));
    }


    /**
     * 4. 搜索与列表 (修改原有的 list 接口，支持 keyword 模糊搜索)
     */
    @GetMapping("/list")
    public Result<?> findPage(@RequestParam(defaultValue = "1") Integer pageNum,
                              @RequestParam(defaultValue = "10") Integer pageSize,
                              @RequestParam(defaultValue = "") String keyword, // 新增 keyword 参数
                              @RequestParam(defaultValue = "") String category) { // 支持按分类查

        Page<SysBook> page = new Page<>(pageNum, pageSize);
        QueryWrapper<SysBook> query = new QueryWrapper<>();

        // 模糊搜索：书名 OR 作者
        if (!keyword.isEmpty()) {
            query.and(wrapper -> wrapper.like("title", keyword).or().like("author", keyword));
        }

        // 分类筛选
        if (!category.isEmpty() && !"全部".equals(category)) {
            query.eq("category", category);
        }

        query.orderByDesc("id");
        return Result.success(sysBookService.page(page, query));
    }
    @PostMapping("/analyze/{bookId}")
    public Result<?> analyzeBook(@PathVariable Long bookId) {
        SysBook book = sysBookService.getById(bookId);
        if (book == null) return Result.error("404", "书籍不存在");

        // 1. 检查是否已经分析过
        QueryWrapper<SysChapter> query = new QueryWrapper<>();
        query.eq("book_id", bookId);
        if (chapterMapper.selectCount(query) > 0) {
            return Result.success("该书已存在章节信息，跳过分析");
        }

        // 2. 读取本地文件
        // 假设 filePath 是 URL (如 http://localhost:8090/files/xxx.txt)
        // 我们需要提取文件名 xxx.txt
        String fileName = book.getFilePath().substring(book.getFilePath().lastIndexOf("/") + 1);

        // === 核心修改：使用统一配置的 uploadPath ===
        // 确保 uploadPath 结尾有斜杠，或者这里手动补一个
        String finalPath = uploadPath.endsWith("/") || uploadPath.endsWith("\\")
                ? uploadPath + fileName
                : uploadPath + File.separator + fileName;

        System.out.println("正在解析文件路径: " + finalPath); // 打印日志方便调试

        File bookFile = new File(finalPath);
        if (!bookFile.exists()) {
            return Result.error("500", "磁盘上找不到文件: " + finalPath + "。请确认文件是否存在或重新上传。");
        }

        // 3. 解析
        List<SysChapter> chapters = ChapterParser.parse(bookId, bookFile);

        // 4. 保存
        // 建议使用 Service 层的 saveBatch，这里为了演示直接循环
        for (SysChapter chapter : chapters) {
            chapterMapper.insert(chapter);
        }

        return Result.success("成功解析出 " + chapters.size() + " 个章节");
    }

    /**
     * 获取书籍目录 (只返回 ID 和 标题)
     */
    @GetMapping("/catalog/{bookId}")
    public Result<List<SysChapter>> getCatalog(@PathVariable Long bookId) {
        return Result.success(chapterMapper.selectCatalog(bookId));
    }

    /**
     * 获取某一章节的详细内容
     */
    @GetMapping("/chapter/{chapterId}")
    public Result<SysChapter> getChapterContent(@PathVariable Long chapterId) {
        return Result.success(chapterMapper.selectById(chapterId));
    }

    @GetMapping("/hot")
    public Result<List<SysBook>> getHotBooks() {
        // 调用自定义的统计查询
        List<SysBook> list = sysBookMapper.selectHotBooks();
        return Result.success(list);
    }
    /**
     * 个性化推荐接口 (AI 驱动)
     */
    @GetMapping("/recommend")
    public Result<List<SysBook>> getRecommendBooks(@RequestParam(required = false) Long userId) {
        // 1. 【开关校验】如果未开启 AI 推荐，直接返回随机推荐 (节省 Token)
        if (!isAiRecommendationEnabled) {
            return Result.success(sysBookMapper.selectRandomBooks());
        }

        // 2. 如果用户未登录，返回随机推荐
        if (userId == null) {
            return Result.success(sysBookMapper.selectRandomBooks());
        }

        // 3. 【缓存校验】检查 Redis 是否已有该用户的推荐结果
        // Key 格式: recommend:uid:123
        String cacheKey = "recommend:uid:" + userId;
        String cachedJson = redisTemplate.opsForValue().get(cacheKey);

        if (StringUtils.hasText(cachedJson)) {
            try {
                // 如果缓存中有，直接解析返回，不再调 AI
                List<SysBook> cachedBooks = objectMapper.readValue(cachedJson, new TypeReference<List<SysBook>>() {});
                return Result.success(cachedBooks);
            } catch (Exception e) {
                // 缓存解析失败，忽略，继续往下走
            }
        }

        // 4. 获取用户阅读历史
        List<String> readHistory = bookshelfMapper.selectBookTitlesByUserId(userId);
        if (readHistory.isEmpty()) {
            return Result.success(sysBookMapper.selectRandomBooks());
        }

        // 5. 调用 AI 获取推荐关键词
        List<String> aiSuggestions = callAiForRecommendation(readHistory);

        // 6. 数据库匹配
        List<SysBook> finalRecommendations = new ArrayList<>();
        Set<Long> addedBookIds = new HashSet<>();

        for (String keyword : aiSuggestions) {
            SysBook book = sysBookMapper.selectOneByKeyword(keyword);
            if (book != null && !addedBookIds.contains(book.getId())) {
                finalRecommendations.add(book);
                addedBookIds.add(book.getId());
            }
        }

        // 7. 补齐随机书
        if (finalRecommendations.size() < 4) {
            List<SysBook> randomBooks = sysBookMapper.selectRandomBooks();
            for (SysBook b : randomBooks) {
                if (!addedBookIds.contains(b.getId())) {
                    finalRecommendations.add(b);
                    addedBookIds.add(b.getId());
                    if (finalRecommendations.size() >= 8) break;
                }
            }
        }

        // 8. 【写入缓存】将最终结果存入 Redis，设置 2 小时过期
        // 这样用户在 2 小时内刷新页面，都不会消耗 AI Token
        try {
            redisTemplate.opsForValue().set(
                    cacheKey,
                    objectMapper.writeValueAsString(finalRecommendations),
                    2, TimeUnit.HOURS
            );
        } catch (Exception e) {
            e.printStackTrace();
        }

        return Result.success(finalRecommendations);
    }

    /**
     * 私有方法：调用大模型获取推荐列表
     */
    private List<String> callAiForRecommendation(List<String> history) {
        try {
            // 构造 Prompt
            String historyStr = String.join("、", history);
            String prompt = String.format(
                    "用户读过这些书：%s。" +
                            "请根据用户的阅读口味，推荐 10 本同类型的经典书籍或畅销书。" +
                            "【严格要求】：只返回一个纯 JSON 字符串数组，不要包含任何 markdown 标记或其他文字。" +
                            "例如：[\"三体\", \"活着\", \"白夜行\"]",
                    historyStr
            );

            Message userMsg = Message.builder().role(Role.USER.getValue()).content(prompt).build();

            GenerationParam param = GenerationParam.builder()
                    .apiKey(apiKey)
                    .model("qwen-turbo") // 推荐任务用 turbo 足够快且便宜
                    .messages(Collections.singletonList(userMsg))
                    .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                    .build();

            Generation gen = new Generation();
            GenerationResult result = gen.call(param);
            String jsonOutput = result.getOutput().getChoices().get(0).getMessage().getContent();

            // 清洗数据：防止 AI 返回 ```json ... ```
            jsonOutput = jsonOutput.replace("```json", "").replace("```", "").trim();

            // 解析 JSON
            return objectMapper.readValue(jsonOutput, new TypeReference<List<String>>() {});

        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>(); // 失败则返回空，让主逻辑走随机推荐
        }
    }
}