# 系统架构

## 概述

智能阅读系统采用前后端分离架构，后端 Spring Boot 提供 REST API 和 WebSocket，前端 Vue 3 提供桌面端和移动端界面，TTS 服务通过 Python FastAPI 提供语音合成。

```
Browser/App
    │
    ├── Desktop (5173) ──→ Nginx ──→ /api → Spring Boot (8090)
    │                              ──→ /ws  → WebSocket
    │                              ──→ /files → 静态文件
    │
    ├── Mobile (5174)  ──→ Nginx ──→ (same backend)
    │
    └── External APIs
          ├── Dify (AI 阅读助手 / 推荐 / 知识库)
          └── edge-tts (语音合成)
```

## 技术栈

| 层 | 技术 |
|------|------|
| 后端框架 | Spring Boot 3.2.2, Java 17 |
| ORM | MyBatis-Plus 3.5.7 |
| 鉴权 | 自研 Token (HMAC-SHA256) + Spring Security |
| 数据库 | MySQL 8.x (smartreader) |
| 缓存 | Redis 7 |
| 搜索引擎 | Elasticsearch 8.x |
| 迁移工具 | Flyway |
| 前端框架 | Vue 3 + Vite |
| UI 库 | Element Plus (桌面) / Vant (移动) |
| AI 平台 | Dify (聊天/推荐/知识库) |
| TTS | edge-tts (Python FastAPI) |
| 部署 | Docker Compose |

## 数据流

### 核心阅读流程
```
用户选择书籍 → GET /sysBook/detail/{id}
              → GET /sysBook/catalog/{id}
              → GET /sysBook/chapter/{id}?index=N
              → POST /bookshelf/updateProgress (阅读进度)
```

### AI 阅读助手
```
用户选中文字 → POST /difyreading/analyze (SSE 流式)
              → 后端转发 Dify Chat API
              → Dify 检索知识库 (metadata 过滤 book_id)
              → 流式返回 AI 回复
```

### 知识库同步
```
书籍上传解析 → SysBookController.analyzeBook
              → ChapterParser 拆分章节
              → DifyKnowledgeBaseService.syncOneBookToKb
              → 逐章 create_by_text (Dify KB API)
              → docId 回写 sys_chapter.kb_document_id
```

### 搜索流程
```
用户搜索 → GET /search?keyword=&category=&pageNum=&pageSize=
         → Elasticsearch 全文检索
         → 返回书籍列表 + 章节内容高亮
```

### WebSocket 聊天
```
用户 A → ws://host:8090/ws/chat
       → WebSocketHandler 路由消息
       → 持久化到 chat_message 表
       → 推送到用户 B
```

## 目录结构

```
reading-system/
├── src/main/java/com/example/reading/
│   ├── controller/    # REST 控制器
│   ├── service/       # 业务逻辑
│   ├── mapper/        # MyBatis Mapper
│   ├── entity/        # 数据库实体
│   ├── dto/           # 请求/响应 DTO
│   ├── config/        # Spring 配置
│   ├── common/        # 通用类 (Result, IpUtil)
│   └── utils/         # 工具类 (ChapterParser, WebSocketHandler)
├── src/main/resources/
│   ├── application.yml
│   └── db/migration/  # Flyway 迁移脚本
├── reading-ui/        # 桌面端 Vue 前端
├── reading-mobile-ui/ # 移动端 Vue 前端
├── lightweight-tts-service/  # Python TTS 服务
├── docker-compose.yml # 全栈部署
└── Dockerfile         # 后端构建
```
