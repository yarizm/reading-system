# 智能阅读系统

![Java](https://img.shields.io/badge/Java-17-orange.svg)
![Spring Boot](https://img.shields.io/badge/SpringBoot-3.2.2-green.svg)
![Vue](https://img.shields.io/badge/Vue.js-3.x-4FC08D.svg)

## 项目简介

智能阅读系统是一个前后端分离的阅读平台，包含 Spring Boot 后端、桌面端 Vue 前端、移动端 Vue 前端和轻量 TTS 服务。系统围绕图书阅读、书架管理、评论互动、好友聊天、AI 阅读助手、首页推荐和听书能力构建，适合课程设计、毕业设计或继续扩展为业务原型。

## 功能特性

- 图书详情展示、章节阅读和阅读进度记录
- 用户书架管理，支持加入、移除和继续阅读
- 书单创建、查看、分享和导入
- 图书评论、回复和点赞
- 段落评论与段落分享
- 好友搜索、好友申请处理和私聊
- 聊天中展示图书分享、段落分享和音频分享
- AI 阅读助手
- 首页推荐
- 段落朗读、整章听书、音频下载和分享
- 后台用户信息管理、图书上传审核和评论内容管理

## 技术栈

| 模块 | 技术 |
| --- | --- |
| 后端 | Java 17, Spring Boot 3.2.2, Spring MVC, MyBatis-Plus, WebSocket |
| 存储与中间件 | MySQL, Redis, Elasticsearch |
| 桌面端 | Vue 3, Vite, Vue Router, Element Plus |
| 移动端 | Vue 3, Vite, Vue Router, Vant |
| AI 与推荐 | Dify, DashScope |
| 听书服务 | FastAPI, uvicorn, edge-tts |

## 项目结构

| 路径 | 说明 |
| --- | --- |
| `src/main` | Spring Boot 后端源码 |
| `src/main/resources/application.yml` | 后端主配置文件 |
| `src/main/resources/db` | 现有数据库补充脚本和迁移脚本 |
| `src/main/resources/mapper` | MyBatis XML Mapper |
| `reading-ui` | 桌面端 Vue 前端 |
| `reading-mobile-ui` | 移动端 Vue 前端 |
| `lightweight-tts-service` | 轻量 TTS 服务 |
| `docker-compose.tts.yml` | 仅用于启动 TTS 服务的 Docker Compose 配置 |
| `pom.xml` | 后端 Maven 配置 |

## 环境要求

| 依赖 | 建议版本 | 说明 |
| --- | --- | --- |
| JDK | 17 | 后端编译和运行 |
| Maven | 3.8+ | 后端依赖管理 |
| Node.js | 22.x | 前端构建；桌面端要求 `^20.19.0 || >=22.12.0` |
| npm | 随 Node 安装 | 前端依赖管理 |
| Python | 3.11 | 本地运行轻量 TTS 服务 |
| MySQL | 8.x | 业务数据库，建议字符集 `utf8mb4` |
| Redis | 6.x+ | 缓存与推荐结果缓存 |
| Elasticsearch | 8.x | 图书搜索 |
| Docker Desktop | 可选 | 容器方式启动 TTS 服务 |

## 基础依赖服务

| 服务 | 用途 | 默认地址/端口 | 是否必需 | 配置位置 |
| --- | --- | --- | --- | --- |
| MySQL | 业务数据存储 | `localhost:3306`，数据库名 `smartreader` | 必需 | `src/main/resources/application.yml` / `MYSQL_PASSWORD` |
| Redis | 缓存、推荐结果缓存等 | `localhost:6379`，database `0` | 视功能而定，推荐启动 | `src/main/resources/application.yml` |
| Elasticsearch | 图书搜索 | `http://localhost:9200` | 搜索功能需要 | `src/main/resources/application.yml` |
| TTS 服务 | 段落朗读、整章听书 | `http://localhost:8091` | 听书功能需要 | `src/main/resources/application.yml` |
| Dify | AI 阅读助手与推荐排序 | 外部地址，默认示例为 `https://api.dify.ai/v1/chat-messages` | AI 功能需要 | `.env.example` 对应变量 / 系统环境变量 |
| DashScope/Qwen | AI 与 DashScope TTS 能力 | 外部服务 | AI 或 DashScope TTS 功能需要 | `.env.example` 对应变量 / 系统环境变量 |

## 配置说明

后端主配置文件为 `src/main/resources/application.yml`，重点配置项包括：

| 配置项 | 说明 |
| --- | --- |
| `server.port` | 后端服务端口，默认 `8090` |
| `spring.datasource.url` | MySQL 地址，默认连接 `jdbc:mysql://localhost:3306/smartreader` |
| `spring.datasource.username` | MySQL 用户名，当前默认 `root` |
| `spring.datasource.password` | MySQL 密码，来自环境变量 `MYSQL_PASSWORD` |
| `spring.data.redis.*` | Redis 连接配置 |
| `spring.elasticsearch.uris` | Elasticsearch 地址，默认 `http://localhost:9200` |
| `file.upload-path` | 上传文件和生成音频文件保存目录，部署前需要确保目录存在且可写 |
| `ai.dashscope.api-key` | DashScope API Key，来自环境变量 `QWEN_API_KEY` |
| `tts.provider` | TTS 提供方，默认 `lightweight` |
| `tts.lightweight.base-url` | 轻量 TTS 服务地址，默认 `http://localhost:8091` |
| `dify.reading.*` | AI 阅读助手 Dify 接口配置 |
| `dify.recommend.*` | 推荐相关 Dify 接口配置 |

当前 `application.yml` 中引用的环境变量与 `.env.example` 一致：

```env
MYSQL_PASSWORD=your_mysql_password
QWEN_API_KEY=your_dashscope_api_key
DIFY_CHAT_URL=https://api.dify.ai/v1/chat-messages
DIFY_READING_KEY=your_dify_reading_key
DIFY_RECOMMEND_KEY=your_dify_recommend_key
```

请复制 `.env.example` 为 `.env` 后按本地环境填写。不要提交 `.env`，也不要把真实数据库密码、DashScope Key 或 Dify Key 写入代码、README 或提交记录。

注意：Spring Boot 默认不会自动读取项目根目录的 `.env` 文件。`.env.example` 只是变量清单模板。开发时可以通过系统环境变量、IDE 运行配置、Shell 启动脚本或部署平台环境变量注入这些值。

## 数据库初始化说明

后端默认连接的数据库为 `smartreader`。建议使用 MySQL 8.x，并使用 `utf8mb4` 字符集：

```sql
CREATE DATABASE smartreader
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;
```

当前仓库存在以下 SQL 文件：

| 文件 | 作用 | 备注 |
| --- | --- | --- |
| `src/main/resources/db/auth.sql` | 给 `sys_user` 增加手机号/邮箱字段，并创建验证码记录表 | 依赖已存在的 `sys_user` |
| `src/main/resources/db/booklist.sql` | 创建书单相关表，并给 `sys_user` 增加书架可见性字段 | 依赖已存在的 `sys_user` |
| `src/main/resources/db/social.sql` | 创建好友、聊天和图书分享相关表 | 分模块脚本 |
| `src/main/resources/db/migration_user_upload.sql` | 给 `sys_book` 和 `sys_user` 增加上传审核相关字段 | 依赖已存在的 `sys_book`、`sys_user` |

这些脚本是补充脚本或迁移脚本，不是完整的一键初始化脚本。仓库当前暂未提供完整数据库初始化 SQL，也没有明确的完整初始管理员账号或测试数据脚本。首次部署需要根据项目实际数据库结构准备基础表，例如用户、图书、章节、评论、书架等核心表，然后再按功能需要执行上述补充脚本。

如果已经具备基础表，建议执行顺序为：

```text
1. src/main/resources/db/auth.sql
2. src/main/resources/db/booklist.sql
3. src/main/resources/db/social.sql
4. src/main/resources/db/migration_user_upload.sql
```

后续建议补充 `sql/init.sql`、Flyway/Liquibase 迁移或按模块拆分的完整初始化脚本。

## 快速启动

### 1. 准备基础环境

安装并确认以下命令可用：

```powershell
java -version
mvn -version
node -v
npm -v
python --version
```

### 2. 准备数据库

创建默认数据库：

```sql
CREATE DATABASE smartreader
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;
```

准备基础业务表后，再按需要执行 `src/main/resources/db` 下的补充脚本。当前仓库没有完整一键初始化 SQL，不能只执行这些补充脚本就得到完整可运行数据库。

### 3. 配置环境变量

PowerShell 示例：

```powershell
Copy-Item .env.example .env
$env:MYSQL_PASSWORD="your_mysql_password"
$env:QWEN_API_KEY="your_dashscope_api_key"
$env:DIFY_CHAT_URL="https://api.dify.ai/v1/chat-messages"
$env:DIFY_READING_KEY="your_dify_reading_key"
$env:DIFY_RECOMMEND_KEY="your_dify_recommend_key"
```

如暂不使用 AI 功能，可以先填写安全占位值，但调用 AI 相关接口时需要真实可用的外部服务配置。

### 4. 启动 TTS 服务

Docker 方式：

```powershell
docker compose -f docker-compose.tts.yml up -d --build
```

`docker-compose.tts.yml` 只启动轻量 TTS 服务，不会启动 MySQL、Redis、Elasticsearch、后端或前端。

本地 Python 方式：

```powershell
cd lightweight-tts-service
python -m pip install -r requirements.txt
python -m uvicorn app:app --host 0.0.0.0 --port 8091
```

当前 TTS 服务入口是 `app.py` 中的 FastAPI `app` 对象，不是直接执行 `python app.py`。

### 5. 启动后端服务

```powershell
mvn spring-boot:run
```

也可以在 IDE 中直接运行 `ReadingSystemApplication`。后端启动前请确认 MySQL 连接、环境变量和 `file.upload-path` 已配置好。

### 6. 启动桌面端前端

```powershell
cd reading-ui
npm install
npm run dev
```

### 7. 启动移动端前端

```powershell
cd reading-mobile-ui
npm install
npm run dev
```

移动端开发服务默认监听 `0.0.0.0`，便于同一局域网内手机访问。实际访问地址以 Vite 终端输出为准。

## 访问地址

| 服务 | 默认地址 | 说明 |
| --- | --- | --- |
| 后端 API | `http://localhost:8090` | REST API、WebSocket、静态文件访问 |
| TTS 服务 | `http://localhost:8091` | `/health`、`/synthesize` |
| 桌面端前端 | `http://localhost:5173` | Vite 默认开发地址 |
| 移动端前端 | `http://localhost:5174` | 如端口占用，Vite 会自动分配其他端口 |

前端开发环境通过 Vite 代理访问后端：

- `reading-ui/vite.config.js` 中 `/api`、`/ws`、`/files` 默认转发到 `http://localhost:8090`
- `reading-mobile-ui/vite.config.js` 中 `/api`、`/ws`、`/files` 默认转发到 `http://localhost:8090`
- 前端当前没有使用 `VITE_API_BASE_URL` 等环境变量；如需更换后端地址，请修改对应 Vite proxy 的 `target`

## Docker Compose 说明

当前仓库只提供 `docker-compose.tts.yml`，它只覆盖轻量 TTS 服务。

它不是完整系统一键部署文件，不会启动：

- MySQL
- Redis
- Elasticsearch
- Spring Boot 后端
- 桌面端前端
- 移动端前端

后续可以补充完整 `docker-compose.yml`，用于一键启动 MySQL、Redis、Elasticsearch、后端、前端和 TTS。

## AI 与听书说明

AI 阅读助手和推荐功能依赖 Dify 与 DashScope 相关配置。若未配置真实 Key，相关接口可能无法得到模型结果，但不影响基础前后端构建。

听书能力默认使用轻量 TTS 服务。后端会调用：

```text
POST http://localhost:8091/synthesize
```

如果 TTS 服务未启动、网络不可达或 edge-tts 生成失败，后端听书接口会返回失败结果或错误信息；基础阅读、书架和非 AI 页面不应依赖 TTS 服务启动。

如果听书不可用，优先检查：

- `GET http://localhost:8091/health` 是否正常
- `tts.provider` 是否为 `lightweight`
- `tts.lightweight.base-url` 是否可访问
- `file.upload-path` 是否存在且可写

## 部署检查清单

- [ ] 已安装 Java 17
- [ ] 已安装 Maven
- [ ] 已安装 Node.js 22 或满足前端 `package.json` 的 Node 版本
- [ ] 已安装 Python 3.11
- [ ] MySQL 已启动并创建 `smartreader` 数据库
- [ ] 已准备完整业务表结构；已按需执行 `src/main/resources/db` 下的补充脚本
- [ ] Redis 已启动，默认可通过 `localhost:6379` 访问
- [ ] Elasticsearch 已启动，默认可通过 `http://localhost:9200` 访问
- [ ] 已配置 `MYSQL_PASSWORD`
- [ ] 如需 AI 功能，已配置 `QWEN_API_KEY`、`DIFY_CHAT_URL`、`DIFY_READING_KEY`、`DIFY_RECOMMEND_KEY`
- [ ] 如需听书功能，TTS 服务已启动并可访问 `/health`
- [ ] `file.upload-path` 指向的目录存在且可写
- [ ] 后端 `8090` 端口可访问
- [ ] 桌面端和移动端 Vite proxy 指向正确后端地址
- [ ] 没有将 `.env` 或真实密钥提交到 Git

## 开发与验证

基础验证命令：

```powershell
mvn -q -DskipTests compile
```

```powershell
cd reading-ui
npm ci
npm run build
```

```powershell
cd reading-mobile-ui
npm ci
npm run build
```

```powershell
cd lightweight-tts-service
python -m pip install -r requirements.txt
python -m py_compile app.py
```

```powershell
git diff --check
```

GitHub Actions 中的基础 CI 仅做编译和构建检查，不连接真实 MySQL、Redis、Elasticsearch，也不需要真实外部 API Key。

## 常见问题

### 后端启动失败

- 检查 MySQL、Redis、Elasticsearch 是否启动
- 检查 `smartreader` 数据库和业务表是否存在
- 检查环境变量是否已注入当前终端或 IDE 运行配置
- 检查 `application.yml` 中的端口、数据库名和上传目录

### 前端请求 404 或连接失败

- 检查后端是否运行在 `8090`
- 检查 Vite 开发服务是否正常启动
- 确认请求路径使用 `/api`、`/ws` 或 `/files`
- 如后端端口变化，修改对应 `vite.config.js` 的 proxy `target`

### 听书失败

- 检查 TTS 服务 `http://localhost:8091/health`
- 检查 `8091` 端口是否被占用
- 检查上传目录是否存在且有写入权限
- 检查运行环境是否能访问 edge-tts 所需网络服务

### 推荐功能无结果

- 检查 Redis 是否可用
- 检查 Dify URL 和 Key 是否正确
- 检查用户是否已有书架数据

## 待改进

- 补充完整数据库初始化脚本，例如 `sql/init.sql` 或 Flyway/Liquibase 迁移
- 补充完整 Docker Compose，用于一键启动 MySQL、Redis、Elasticsearch、后端、前端和 TTS
- 增加后端测试隔离配置，避免测试依赖真实外部服务
- 增加前端 lint 与依赖安全审计流程
- 补充接口文档和部署环境变量说明

## 许可证

本项目使用 MIT License，详见 `LICENSE`。
