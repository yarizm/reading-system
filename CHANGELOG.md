# Changelog

## v0.2.0

### Added
- Dify 知识库章节级自动同步（DifyKnowledgeBaseService）
- 知识库文档元数据创建时自动写入（book_id + book_name）
- 阅读助手 AI 回复 Markdown 渲染（marked.js）
- AI 助手面板拖拽缩放（300-800px）和左右位置切换
- Docker Compose 全栈一键部署（MySQL + Redis + ES + 后端 + 前端 + TTS）
- Flyway 数据库自动迁移
- 后端单元测试（AuthContextService + DifyKnowledgeBaseService）
- 前端组件测试（BookDetail）
- GitHub PR/Issue 模板

### Changed
- KB 同步从书级改为章节级，避免大文本 embedding 超时
- 数据库迁移由手动 SQL 改为 Flyway 自动管理
- CI 从仅编译改为编译 + 测试

### Fixed
- 非公开书籍搜索泄漏（BookSearchService null 书清理 + BookDetail 错误状态）
- 待审核书籍（status=1）允许编辑提交信息（MyBooks.vue）
- 目录侧边栏打开动画卡顿（GPU 层提升 + transition 精准化）
- Element Plus drawer `#title` → `#header` 槽位迁移
- 仓库元数据补充（Description + Topics）

### Removed
- 死代码清理（SysValidation + AiInteractionLog）
- sys_book.kb_document_id 废弃字段

---

## v0.1.0

### Added
- Spring Boot 后端基础服务
- 桌面端 Vue 阅读界面
- 移动端 Vue 阅读界面
- 图书、章节、书架、评论、好友、聊天等核心模块
- AI 阅读助手与推荐相关配置
- 轻量 TTS 服务与听书能力
- Docker Compose 启动 TTS 服务
- Maven Wrapper
- 部署说明（基础依赖、数据库初始化、配置说明、故障排查）
