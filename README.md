# 智能阅读系统

![Java](https://img.shields.io/badge/Java-17-orange.svg)
![Spring Boot](https://img.shields.io/badge/SpringBoot-3.2.2-green.svg)
![Vue](https://img.shields.io/badge/Vue.js-3.x-4FC08D.svg)

## 项目简介

智能阅读系统是一个前后端分离的阅读平台，当前已包含 Spring Boot 后端、桌面端 Vue 前端、移动端 Vue 前端、轻量 TTS 服务、Flyway 数据库迁移、Docker Compose 本地部署和基础自动化测试。

系统主要覆盖图书阅读、书架管理、书单分享、评论互动、好友聊天、AI 阅读助手、知识库同步、搜索推荐和听书能力，适合课程设计、毕业设计展示，也可以作为继续扩展的业务原型。

## 当前完成度

已完成并可运行：

- 用户注册、登录、验证码登录、密码重置、Token 鉴权和封禁控制
- 图书上传、章节解析、图书详情、目录、章节阅读和阅读进度保存
- 用户书架、个人上传图书、公开申请、编辑审核和下架申请
- 书评、回复、点赞、段落评论、段落分享和笔记
- 好友搜索、好友申请、好友列表、私聊、未读消息和分享消息
- 书单创建、书籍加入、分享码导入
- Elasticsearch 图书搜索，使用 Docker 兼容的内置 `cjk` 分词
- Dify 阅读助手 SSE 流式输出和 Dify 知识库章节级同步
- 轻量 TTS 听书能力
- 桌面端和移动端前端
- Docker Compose 启动 MySQL、Redis、Elasticsearch、后端、两个前端和 TTS
- Flyway V0 baseline 自动建表
- 后端 JUnit 测试、桌面端 Vitest 测试和 GitHub Actions 基础 CI

仍需按环境准备或手工验收：

- AI、推荐、知识库和 TTS 依赖外部服务 Key 或网络能力
- Elasticsearch mapping 变更后需要重建 `es_book` 索引并重新同步图书
- 旧环境数据迁移需要自行从旧库导出并导入，不再把真实 dump 放入仓库
- 生产部署需要额外加固密钥、HTTPS、ES 安全认证、日志和备份策略

## 技术栈

| 模块 | 技术 |
| --- | --- |
| 后端 | Java 17, Spring Boot 3.2.2, Spring MVC, WebFlux, Spring Security, MyBatis-Plus, WebSocket, Flyway, dify-spring-boot-starter |
| 存储与中间件 | MySQL 8, Redis 7, Elasticsearch 8 |
| 桌面端 | Vue 3, Vite, Pinia, Vue Router, Element Plus, Vitest |
| 移动端 | Vue 3, Vite, Pinia, Vue Router, Vant |
| AI 与推荐 | Dify |
| 听书服务 | FastAPI, uvicorn, edge-tts |
| 部署 | Docker, Docker Compose, Nginx |

## 项目结构

| 路径 | 说明 |
| --- | --- |
| `src/main` | Spring Boot 后端源码 |
| `src/test` | 后端单元测试 |
| `src/main/resources/db/migration` | Flyway 数据库迁移脚本，目前为 `V0__baseline.sql` |
| `src/main/resources/mapper` | MyBatis XML Mapper |
| `reading-ui` | 桌面端 Vue 前端 |
| `reading-ui/src/__tests__` | 桌面端组件测试 |
| `reading-mobile-ui` | 移动端 Vue 前端 |
| `lightweight-tts-service` | 轻量 TTS FastAPI 服务 |
| `docker-compose.yml` | 本地全栈 Docker Compose，默认不依赖外部 Docker 网络 |
| `docker-compose.dify.yml` | 可选 Dify Docker 网络覆盖配置 |
| `docker-compose.tts.yml` | 单独启动 TTS 服务 |
| `Dockerfile` | 后端多阶段构建 |
| `docs/` | 架构、API 和答辩材料，允许正常提交跟踪 |

## 环境变量

复制 `.env.example` 为 `.env`，Docker Compose 会读取该文件。本地手动运行 Spring Boot 时，仍需要通过终端、IDE 运行配置或系统环境变量注入。

```env
MYSQL_PASSWORD=your_mysql_password

APP_AUTH_TOKEN_SECRET=change_this_to_a_long_random_secret
APP_AUTH_TOKEN_TTL_MILLIS=86400000

DIFY_BASE_URL=https://api.dify.ai/v1
DIFY_CHAT_URL=https://api.dify.ai/v1
DIFY_READING_KEY=your_dify_reading_key
DIFY_RECOMMEND_KEY=your_dify_recommend_key

DIFY_KB_URL=https://api.dify.ai/v1
DIFY_KB_KEY=dataset-xxxx
DIFY_KB_DATASET_ID=your-dataset-uuid
```

生产或公开部署必须替换 `APP_AUTH_TOKEN_SECRET`，不要提交 `.env` 或真实密钥。

## Docker 一键启动

本仓库当前推荐用 Docker Compose 做本地完整体验。默认配置只启动本仓库服务，不要求本机存在 Dify 的 `docker_default` 外部网络。

```bash
cp .env.example .env
# 编辑 .env，至少填写 MYSQL_PASSWORD；需要 AI/TTS 完整能力时填写对应 Key
docker compose up -d
docker compose ps
```

默认访问地址：

| 服务 | 地址 |
| --- | --- |
| 桌面端 | `http://localhost:5173` |
| 移动端 | `http://localhost:5174` |
| 后端 API | `http://localhost:8090` |
| TTS 健康检查 | `http://localhost:8091/health` |
| Elasticsearch | `http://localhost:9200` |
| MySQL | `localhost:3306`，数据库 `smartreader` |
| Redis | `localhost:6379` |

停止服务：

```bash
docker compose down
docker compose down -v
```

`down -v` 会删除 MySQL、Redis、Elasticsearch 和上传文件数据卷，仅在需要重置环境时使用。

### 连接本地 Docker Dify

如果 Dify 也在同一台机器的 Docker Compose 中运行，并且需要后端容器通过 Dify 容器网络访问它，可以额外叠加 override 文件：

```bash
docker compose -f docker-compose.yml -f docker-compose.dify.yml up -d
```

该方式会把 `backend` 接入 `${DIFY_DOCKER_NETWORK:-docker_default}`，并默认使用 `http://docker-api-1:5001/v1` 作为 Dify 内网地址。网络名或地址不一致时，在 `.env` 中覆盖 `DIFY_DOCKER_NETWORK`、`DIFY_DOCKER_CHAT_URL` 和 `DIFY_DOCKER_KB_URL`。

## 手动本地启动

### 1. 准备基础服务

需要本机可访问：

- MySQL 8，数据库名 `smartreader`
- Redis，默认 `localhost:6379`
- Elasticsearch 8，默认 `http://localhost:9200`
- 可选：TTS 服务，默认 `http://localhost:8091`

创建数据库：

```sql
CREATE DATABASE smartreader
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;
```

后端启动时 Flyway 会自动执行 `src/main/resources/db/migration/V0__baseline.sql` 创建表结构。

### 2. 启动后端

Windows PowerShell：

```powershell
$env:MYSQL_PASSWORD="your_mysql_password"
$env:APP_AUTH_TOKEN_SECRET="change_this_to_a_long_random_secret"
$env:DIFY_CHAT_URL="https://api.dify.ai/v1"
$env:DIFY_READING_KEY="your_dify_reading_key"
$env:DIFY_RECOMMEND_KEY="your_dify_recommend_key"
$env:DIFY_KB_URL="https://api.dify.ai/v1"
$env:DIFY_KB_KEY="dataset-xxxx"
$env:DIFY_KB_DATASET_ID="your-dataset-uuid"
.\mvnw.cmd spring-boot:run
```

Linux/macOS：

```bash
export MYSQL_PASSWORD=your_mysql_password
export APP_AUTH_TOKEN_SECRET=change_this_to_a_long_random_secret
export DIFY_CHAT_URL=https://api.dify.ai/v1
export DIFY_READING_KEY=your_dify_reading_key
export DIFY_RECOMMEND_KEY=your_dify_recommend_key
export DIFY_KB_URL=https://api.dify.ai/v1
export DIFY_KB_KEY=dataset-xxxx
export DIFY_KB_DATASET_ID=your-dataset-uuid
./mvnw spring-boot:run
```

如暂不使用 AI 功能，可以先填占位值；调用 AI、推荐或知识库接口时需要真实可用配置。

### 3. 启动前端

桌面端：

```powershell
cd reading-ui
npm ci
npm run dev
```

移动端：

```powershell
cd reading-mobile-ui
npm ci
npm run dev
```

两个前端的 Vite proxy 都默认将 `/api`、`/ws`、`/files` 转发到 `http://localhost:8090`。如后端端口变化，修改对应 `vite.config.js`。

### 4. 启动 TTS

只启动 TTS 容器：

```powershell
docker compose -f docker-compose.tts.yml up -d
```

或本地 Python：

```powershell
cd lightweight-tts-service
python -m pip install -r requirements.txt
python -m uvicorn app:app --host 0.0.0.0 --port 8091
```

## 数据库与搜索

- Flyway 已启用：`spring.flyway.enabled=true`，迁移位置为 `classpath:db/migration`。
- `V0__baseline.sql` 是完整建表基线，不再需要手动执行旧的零散 SQL。
- 已有数据库首次启用 Flyway 时会使用 `baseline-on-migrate=true` 建立基线记录。
- 历史真实数据 dump 已从 `src/main/resources` 移除，避免打包进 jar 或泄露隐私。
- Elasticsearch 文本字段使用内置 `cjk` analyzer，兼容官方 Docker ES 镜像。
- 如果修改过 `EsBookDoc` mapping，需要删除并重建 `es_book` 索引，再调用搜索同步入口重新写入数据。

## 开发验证

后端测试：

```powershell
.\mvnw.cmd test -q
```

桌面端测试：

```powershell
cd reading-ui
npm test -- --run
```

前端构建：

```powershell
cd reading-ui
npm run build

cd ..\reading-mobile-ui
npm run build
```

TTS 入口检查：

```powershell
cd lightweight-tts-service
python -m py_compile app.py
```

提交前建议：

```powershell
git diff --check
```

GitHub Actions 当前使用 Maven Wrapper 运行后端 JUnit 测试，并运行桌面端测试和两个前端构建；CI 不连接真实 MySQL、Redis、Elasticsearch，也不需要真实外部 API Key。

## 常见问题

### Docker 启动后后端无法连接数据库

- 确认 `.env` 中 `MYSQL_PASSWORD` 已设置
- 使用 `docker compose ps` 查看 `reading-mysql` 是否 healthy
- 修改 `.env` 后需要重新创建相关容器，而不只是 `restart`

### Flyway 校验失败

已应用过的迁移不要随意改内容。开发环境需要重置时，可以删除数据卷：

```bash
docker compose down -v
docker compose up -d
```

如果要保留数据，需要按 Flyway 规则新增更高版本迁移脚本，而不是修改已执行的脚本。

### 中文搜索结果不符合预期

- 确认 ES 索引是在当前 `cjk` mapping 下新建的
- 删除旧 `es_book` 索引后重新执行全量同步
- Docker 官方 ES 镜像不含 IK 插件，本项目默认不依赖 IK

### AI 阅读助手无响应

- 检查 `DIFY_CHAT_URL`、`DIFY_READING_KEY` 是否有效
- Dify 也在 Docker 中运行时，后端容器不能用 `localhost` 访问 Dify；使用 `docker-compose.dify.yml` 接入 Dify 网络，或在 `.env` 中配置可访问的 Dify 地址
- Nginx 代理 SSE 时需要关闭 buffering，仓库内前端 Nginx 配置已包含 `proxy_buffering off`

### TTS 不可用

- 检查 `GET http://localhost:8091/health`
- 检查 `tts.lightweight.base-url` 是否指向正确地址
- 检查上传目录是否存在且可写
- edge-tts 需要外部网络能力，网络不可达时听书接口会失败

## 许可证

本项目使用 MIT License，详见 `LICENSE`。
