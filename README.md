# 智能阅读系统

![Java](https://img.shields.io/badge/Java-17-orange.svg)
![Spring Boot](https://img.shields.io/badge/SpringBoot-3.2.2-green.svg)
![Vue](https://img.shields.io/badge/Vue.js-3.x-4FC08D.svg)

## 项目简介
这是一个前后端分离的阅读系统，包含桌面端、移动端和 Spring Boot 后端。系统围绕“图书阅读、书架管理、评论互动、好友社交、AI 辅助阅读和听书”展开，适合做课程设计、毕业设计或继续扩展的业务原型。

## 主要功能
### 阅读与书架
- 图书详情展示、章节阅读、阅读进度记录
- 用户书架管理，支持加入、移除、继续阅读
- 书单创建、查看、分享、导入

### 评论与互动
- 图书评论、回复、点赞
- 段落评论与段落分享
- 图书分享给好友

### 社交功能
- 搜索用户、添加好友、处理好友申请
- 好友私聊
- 聊天中展示图书分享、段落分享、音频分享

### AI 与听书
- AI 阅读助手
- 首页推荐
- 段落朗读、整章听书
- 轻量 TTS 服务生成音频，支持下载与分享

### 后台与审核
- 用户信息管理
- 图书上传与审核
- 评论内容管理

## 技术栈
### 后端
- Java 17
- Spring Boot 3.2.2
- Spring MVC
- MyBatis-Plus
- MySQL
- Redis
- Elasticsearch
- WebSocket

### 前端
- Vue 3
- Vite
- Vue Router
- Element Plus（桌面端）
- Vant（移动端）

### AI 与语音
- Dify
- DashScope
- 轻量 TTS 服务（`lightweight-tts-service`）

## 项目结构
```text
reading-system/
├─ src/                         后端源码
├─ reading-ui/                  桌面端前端
├─ reading-mobile-ui/           移动端前端
├─ lightweight-tts-service/     轻量 TTS 服务
├─ docker-compose.tts.yml       TTS 服务 Docker 编排
├─ pom.xml                      后端 Maven 配置
└─ README.md
```

## 运行环境
启动前建议准备以下环境：

- JDK 17
- Node.js 18 及以上
- MySQL 8.x
- Redis
- Elasticsearch
- Python 3.11（如果本地直接运行轻量 TTS 服务）
- Docker Desktop（如果通过容器运行轻量 TTS 服务）

## 配置说明
后端主配置文件为：

[application.yml](reading-system/src/main/resources/application.yml)

需要重点确认以下配置：

- MySQL 连接信息
- Redis 连接信息
- Elasticsearch 地址
- 文件上传目录 `file.upload-path`
- Dify 相关配置
- TTS 配置

当前配置中使用了以下环境变量：

- `MYSQL_PASSWORD`
- `QWEN_API_KEY`
- `DIFY_CHAT_URL`
- `DIFY_READING_KEY`
- `DIFY_RECOMMEND_KEY`

## 启动方式
### 1. 启动基础依赖
先确保以下服务可用：

- MySQL
- Redis
- Elasticsearch

### 2. 启动轻量 TTS 服务
有两种方式。

方式一：使用 Docker
```powershell
cd reading-system
docker compose -f docker-compose.tts.yml up -d --build
```

方式二：本地直接运行
```powershell
cd reading-system\lightweight-tts-service
pip install -r requirements.txt
uvicorn app:app --host 0.0.0.0 --port 8091
```

### 3. 启动后端
如果本机已安装 Maven：
```powershell
cd reading-system
mvn spring-boot:run
```

如果你使用 IDEA，也可以直接运行：

- `ReadingSystemApplication`

默认端口：

- 后端：`8090`

### 4. 启动桌面端
```powershell
cd reading-system\reading-ui
npm install
npm run dev
```

### 5. 启动移动端
```powershell
cd reading-system\reading-mobile-ui
npm install
npm run dev
```

## 访问说明
启动完成后，可分别访问桌面端和移动端开发地址。具体端口以 Vite 控制台输出为准，通常为：

- 桌面端：`http://localhost:5173`
- 移动端：`http://localhost:5174` 或其他空闲端口

后端接口前缀主要为：

- `/api/*`

静态文件访问路径：

- `/files/*`

## 听书相关说明
当前整章听书与段落朗读都依赖轻量 TTS 服务。后端会调用：

- `http://localhost:8091/synthesize`

如果听书功能不可用，优先检查：

- TTS 服务是否启动
- `tts.provider` 是否为 `lightweight`
- `tts.lightweight.base-url` 是否正确
- 文件上传目录是否可写

## 推荐功能说明
首页推荐逻辑当前包含三层：

1. 基于用户书架的协同过滤候选
2. 调用 Dify 做推荐排序与补齐
3. 出错时回退到随机推荐

推荐结果会缓存到 Redis，避免每次刷新首页都重复调用模型。

## 日志说明
后端日志已做分级控制：

- 业务正常运行信息：`INFO`
- 降级、缓存失败等非致命问题：`WARN`
- 解析失败、TTS 失败等异常：`ERROR`

默认会抑制大部分框架和 SQL 噪音。如果需要排查验证码或更细的业务过程，可以临时把对应包的日志级别调到 `DEBUG`。

## 常见问题
### 1. 后端启动失败
优先检查：

- MySQL、Redis、Elasticsearch 是否启动
- 环境变量是否已配置
- `application.yml` 中的路径和端口是否正确

### 2. 听书失败
优先检查：

- 轻量 TTS 服务是否正常运行
- `8091` 端口是否可访问
- 上传目录是否存在且有写权限

### 3. 推荐功能无结果
优先检查：

- Redis 是否可用
- Dify 配置是否正确
- 用户是否已有书架数据

## 说明
- 根目录 README 主要介绍整体项目。
- 轻量 TTS 服务、桌面端和移动端目录下各自也有独立 README，可按需查看。
