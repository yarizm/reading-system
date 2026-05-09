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
| `src/main/resources/db` | 数据库脚本 |
| `src/main/resources/mapper` | MyBatis XML Mapper |
| `reading-ui` | 桌面端 Vue 前端 |
| `reading-mobile-ui` | 移动端 Vue 前端 |
| `lightweight-tts-service` | 轻量 TTS 服务 |
| `docker-compose.tts.yml` | TTS 服务 Docker Compose 配置 |
| `pom.xml` | 后端 Maven 配置 |

## 环境要求

| 依赖 | 建议版本 | 说明 |
| --- | --- | --- |
| JDK | 17 | 后端编译和运行 |
| Maven | 3.8+ | 后端依赖管理 |
| Node.js | 22.x | 前端构建；桌面端要求 `^20.19.0 || >=22.12.0` |
| npm | 随 Node 安装 | 前端依赖管理 |
| Python | 3.11 | 本地运行轻量 TTS 服务 |
| MySQL | 8.x | 业务数据库 |
| Redis | 6.x+ | 缓存与推荐结果缓存 |
| Elasticsearch | 8.x | 图书搜索 |
| Docker Desktop | 可选 | 容器方式启动 TTS 服务 |

## 配置说明

后端主配置文件为 `src/main/resources/application.yml`，重点配置项包括：

| 配置项 | 说明 |
| --- | --- |
| `server.port` | 后端服务端口，默认 `8090` |
| `spring.datasource.*` | MySQL 连接配置 |
| `spring.data.redis.*` | Redis 连接配置 |
| `spring.elasticsearch.uris` | Elasticsearch 地址 |
| `file.upload-path` | 上传文件和生成音频文件保存目录 |
| `ai.dashscope.api-key` | DashScope API Key，来自环境变量 |
| `tts.provider` | TTS 提供方，默认 `lightweight` |
| `tts.lightweight.base-url` | 轻量 TTS 服务地址，默认 `http://localhost:8091` |
| `dify.reading.*` | AI 阅读助手 Dify 接口配置 |
| `dify.recommend.*` | 推荐相关 Dify 接口配置 |

当前配置依赖以下环境变量：

```env
MYSQL_PASSWORD=your_mysql_password
QWEN_API_KEY=your_dashscope_api_key
DIFY_CHAT_URL=https://api.dify.ai/v1/chat-messages
DIFY_READING_KEY=your_dify_reading_key
DIFY_RECOMMEND_KEY=your_dify_recommend_key
```

请复制 `.env.example` 为 `.env` 后按本地环境填写。不要提交 `.env`，也不要把真实数据库密码、DashScope Key 或 Dify Key 写入代码、README 或提交记录。Spring Boot 默认不会自动读取 `.env` 文件，开发时可在 IDE 运行配置、系统环境变量或启动脚本中注入这些变量。

## 快速启动

### 1. 启动基础依赖

先确保以下服务可用：

- MySQL
- Redis
- Elasticsearch

根据 `src/main/resources/db` 中的脚本准备数据库结构和基础数据，并确认 `application.yml` 中的数据库名、账号、端口和上传目录符合本地环境。

### 2. 准备环境变量

PowerShell 示例：

```powershell
Copy-Item .env.example .env
$env:MYSQL_PASSWORD="your_mysql_password"
$env:QWEN_API_KEY="your_dashscope_api_key"
$env:DIFY_CHAT_URL="https://api.dify.ai/v1/chat-messages"
$env:DIFY_READING_KEY="your_dify_reading_key"
$env:DIFY_RECOMMEND_KEY="your_dify_recommend_key"
```

### 3. 启动轻量 TTS 服务

Docker 方式：

```powershell
docker compose -f docker-compose.tts.yml up -d --build
```

本地 Python 方式：

```powershell
cd lightweight-tts-service
python -m pip install -r requirements.txt
python -m uvicorn app:app --host 0.0.0.0 --port 8091
```

### 4. 启动后端

```powershell
mvn spring-boot:run
```

也可以在 IDE 中直接运行 `ReadingSystemApplication`。

### 5. 启动桌面端

```powershell
cd reading-ui
npm install
npm run dev
```

### 6. 启动移动端

```powershell
cd reading-mobile-ui
npm install
npm run dev
```

## 访问地址

| 服务 | 默认地址 | 说明 |
| --- | --- | --- |
| 后端 | `http://localhost:8090` | REST API、WebSocket、静态文件访问 |
| TTS 服务 | `http://localhost:8091` | `/health`、`/synthesize` |
| 桌面端 | `http://localhost:5173` | Vite 默认开发地址 |
| 移动端 | `http://localhost:5174` | 如端口占用，Vite 会自动分配其他端口 |

前端开发环境通过 Vite 代理访问后端：

- `/api/*` 转发到 `http://localhost:8090`
- `/ws` 转发 WebSocket 到 `http://localhost:8090`
- `/files/*` 转发静态文件访问到 `http://localhost:8090`

## AI 与听书说明

AI 阅读助手和推荐功能依赖 Dify 与 DashScope 相关配置。若未配置真实 Key，相关接口可能无法得到模型结果，但不影响基础前后端构建。

听书能力默认使用轻量 TTS 服务。后端会调用：

```text
http://localhost:8091/synthesize
```

如果听书不可用，优先检查：

- `lightweight-tts-service` 是否启动
- `tts.provider` 是否为 `lightweight`
- `tts.lightweight.base-url` 是否可访问
- `file.upload-path` 是否存在且可写

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

GitHub Actions 中的基础 CI 仅做编译和构建检查，不连接真实 MySQL、Redis 或 Elasticsearch。

## 常见问题

### 后端启动失败

- 检查 MySQL、Redis、Elasticsearch 是否启动
- 检查环境变量是否已注入当前终端或 IDE 运行配置
- 检查 `application.yml` 中的端口、数据库名和上传目录

### 前端请求 404 或连接失败

- 检查后端是否运行在 `8090`
- 检查 Vite 开发服务是否正常启动
- 确认请求路径使用 `/api`、`/ws` 或 `/files`

### 听书失败

- 检查 TTS 服务 `http://localhost:8091/health`
- 检查 `8091` 端口是否被占用
- 检查上传目录是否存在且有写入权限

### 推荐功能无结果

- 检查 Redis 是否可用
- 检查 Dify URL 和 Key 是否正确
- 检查用户是否已有书架数据

## 许可证

本项目使用 MIT License，详见 `LICENSE`。
