# 仓库规范化开发计划

## Context

仓库审查发现已具备基础要素（README、LICENSE、CI、CLAUDE.md、Maven Wrapper），但在可部署性、可测试性、可发现性和代码可维护性方面存在短板。本计划分阶段补齐这些缺口。

难度评估：★ 简单（纯配置/文案）  ★★ 中等（涉及代码）  ★★★ 复杂（涉及架构变更）

---

## 阶段一：仓库元数据与社区规范

### 1.1 仓库元数据
**文件**: GitHub 仓库 Settings  
**难度**: ★

- [ ] Description: `智能阅读系统 — 前后端分离的在线阅读平台，支持 AI 阅读助手、听书、书架管理、好友社交`
- [ ] Topics: `spring-boot` `vue3` `reading` `rag` `dify` `tts` `elasticsearch` `knowledge-base`
- [ ] Website: 可留空或指向 README

### 1.2 PR 模板
**新建**: `.github/PULL_REQUEST_TEMPLATE.md`  
**难度**: ★

```markdown
## 变更说明

## 测试方式

## 影响范围
- [ ] 后端
- [ ] 桌面前端
- [ ] 移动前端
- [ ] TTS 服务
- [ ] 数据库
```

### 1.3 Issue 模板
**新建**: `.github/ISSUE_TEMPLATE/bug_report.md` + `feature_request.md`  
**难度**: ★

两个模板：Bug 报告（含复现步骤、预期行为、截图）和功能请求（含使用场景、期望效果）。

---

## 阶段二：一键部署（Docker Compose）

### 2.1 全栈 Docker Compose
**新建**: `docker-compose.yml`  
**难度**: ★★

涵盖容器：
- MySQL 8.x（端口 3306，数据库 `smartreader` 自动创建）
- Redis 7（端口 6379）
- Elasticsearch 8.x（端口 9200）
- Spring Boot 后端（端口 8090，依赖 MySQL/Redis/ES 健康检查后启动）
- 桌面前端 Nginx 静态服务（端口 5173）
- TTS 服务（端口 8091）

关键设计点：
- 后端用 `Dockerfile` 构建（Maven 编译 + JRE 运行，多阶段构建）
- 前端用 `Dockerfile` 构建（npm ci + npm run build + nginx）
- `depends_on` + `healthcheck` 保证启动顺序
- `.env` 文件由 `.env.example` 复制，用户只需填密钥
- volume 挂载上传目录和数据库数据

### 2.2 后端 Dockerfile
**新建**: `Dockerfile`（项目根目录）  
**难度**: ★★

多阶段构建：
- 阶段 1：`maven:3.9-eclipse-temurin-17` 编译 `mvn package -DskipTests`
- 阶段 2：`eclipse-temurin:17-jre` 运行 jar

### 2.3 前端 Dockerfile
**新建**: `reading-ui/Dockerfile`, `reading-mobile-ui/Dockerfile`  
**难度**: ★

Nginx 静态文件服务，多阶段构建。

### 2.4 更新 README
**文件**: `README.md`  
**难度**: ★

新增"Docker 一键部署"章节，替换手动安装说明为首选方案。

---

## 阶段三：测试框架搭建

### 3.1 后端单元测试
**文件**: `src/test/java/com/example/reading/`  
**难度**: ★★

目标：2-3 个 Service 层集成测试作为起点。

- `DifyKnowledgeBaseServiceTest`：Mock WebClient，验证章节文档创建逻辑
- `AuthContextServiceTest`：验证权限判断（管理员 vs 普通用户 vs 书本所有者）

使用 `spring-boot-starter-test` + Mockito（已在 pom.xml 依赖中）。

### 3.2 前端组件测试
**文件**: `reading-ui/src/__tests__/`  
**难度**: ★★

- `BookDetail.spec.js`（Vitest + @vue/test-utils）：验证加载状态、错误状态、正常渲染

### 3.3 CI 补充
**文件**: `.github/workflows/test.yml`  
**难度**: ★

在后端 Job 中补充 `mvn test`（当前只 `mvn compile`），在前端 Job 中补充 `npm test`。

---

## 阶段四：数据库迁移规范化

### 4.1 Flyway 集成
**文件**: `pom.xml`, `src/main/resources/application.yml`, `src/main/resources/db/migration/`  
**难度**: ★★

- 添加 Flyway 依赖（Spring Boot 自动配置）
- 将现有 SQL 按 `V<version>__<description>.sql` 命名规范迁移到 `db/migration/`
  - `V1__auth.sql`
  - `V2__booklist.sql`
  - `V3__social.sql`
  - `V4__migration_user_upload.sql`
  - `V5__book_review_request.sql`
  - `V6__kb_sync.sql`
- 删除旧的 `src/main/resources/db/*.sql` 或保留为文档
- Flyway 会在应用启动时自动执行未执行过的迁移

### 4.2 README 更新
**文件**: `README.md`  
**难度**: ★

移除手动执行 SQL 顺序的说明，改为"迁移由 Flyway 自动管理"。

---

## 阶段五：代码拆分

### 5.1 Read.vue 组件化
**文件**: `reading-ui/src/views/Read.vue` → 拆分为多个组件  
**难度**: ★★★

当前 ~1800 行，建议拆分：

| 组件 | 职责 | 预估行数 |
|------|------|---------|
| `Read.vue` | 主页面，组合子组件 | ~400 |
| `AIAssistantPanel.vue` | AI 聊天抽屉 | ~400 |
| `CatalogDrawer.vue` | 目录侧边栏 | ~100 |
| `ReadingContent.vue` | 阅读正文区域 | ~300 |
| `useReadingProgress.js` | 阅读进度 composable | ~100 |
| `useAIchat.js` | AI 对话逻辑 composable | ~250 |

Composable 抽离业务逻辑，组件只负责渲染。

---

## 阶段六：文档与维护

### 6.1 更新 CHANGELOG
**文件**: `CHANGELOG.md`  
**难度**: ★

补充 v0.2.0 以来的所有功能（UI 修复、AI 助手增强、KB 同步、元数据过滤等）。

### 6.2 API 文档
**新建**: `docs/API.md`  
**难度**: ★★

列出所有 REST API 端点、请求/响应示例，便于前端联调和第三方接入。

### 6.3 架构图
**新建**: `docs/architecture.md`  
**难度**: ★

文字描述系统架构和数据流：用户请求 → Nginx → Spring Boot → MySQL/Redis/ES → Dify → 响应。

---

## 执行优先级

按投入产出比排序：

1. **阶段一** — 30 分钟，立竿见影提升仓库形象
2. **阶段二.1 + 2.4** — 全栈 Docker Compose + README 更新，大幅降低新人门槛
3. **阶段四** — Flyway 迁移，根除数据库管理混乱
4. **阶段三.1** — 后端核心 Service 测试，最关键的代码质量保障
5. **阶段一.1** — 仓库元数据（需在 GitHub 页面手动操作）
6. **阶段二.2-2.3** — Dockerfile（如不做 Docker 多阶段构建可以先跳过）
7. **阶段五** — Read.vue 拆分（功能稳定后再做）
8. **阶段六** — 文档补充
