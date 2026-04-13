# 智能书籍阅读系统 (Smart Reading System)

![License](https://img.shields.io/badge/license-MIT-blue.svg)
![Java](https://img.shields.io/badge/Java-17-orange.svg)
![Spring Boot](https://img.shields.io/badge/SpringBoot-3.2.2-green.svg)
![Vue](https://img.shields.io/badge/Vue.js-3.x-4FC08D.svg)

## 📖 项目简介 (Introduction)
本项目是一个功能完善、支持多端的智能书籍阅读和社交平台。系统采用前后端分离架构，提供了从图书推荐、全文检索、阅读与书架管理到用户社交（好友互动、即时聊天、书籍及段落分享）的完整闭环体验。同时，系统创造性地集成了基于大语言模型（LLM）的 AI 阅读助手，能够辅助读者进行深度阅读、段落分析与答疑。

## ✨ 核心特性 (Key Features)

- **📚 沉浸式阅读与资源管理**
  - 用户可以自由上传书籍资源，系统支持书籍的私有化阅读或经管理员审核后全站公开上架。
  - 个人专属藏书架，支持多终端阅读进度自动追踪与无缝同步。
  - 基于定制化规则和用户状态构建的首页书籍智能推荐。

- **🔍 专业级全文检索引擎**
  - 深度集成 Elasticsearch 8.x 与 IK 中文分词器，实现毫秒级的书籍元数据（标题、作者、简介）与章节内容的全文精准检索。

- **🤖 流式 AI 智能阅读助手**
  - 接入第三方大语言模型（兼容阿里云通义千问 / Dify AI）。
  - 后端采用 WebFlux 异步调用，前端基于 `@microsoft/fetch-event-source` 及 Server-Sent Events (SSE) 协议，实现 AI 解析结果的打字机式流式实时输出，极大提升人机交互体验。

- **💬 细粒度社交互动与交流**
  - **段落级批注（核心）**：除了常规的整书树形评论外，用户可精准定位到具体章节的特定段落发表评论，犹如“阅读弹幕”。
  - 完整的社交连结：支持检索添加好友、基于 WebSocket 的双向双工即时通讯（私聊）、以及全局消息状态提醒机制。
  - 以书会友：支持一键向好友分享全书，或直接定位分享特定段落。

- **🛡️ 强大的后台维稳与管理体系**
  - 提供系统高优权限账户，支持一键封禁/解禁违规用户。
  - 图书审核制，主导新资源的发布流转流程。
  - 全局社交治理：管理员可跨越权限壁垒，直接移除违规的评论或者批注内容。

## 🛠️ 技术栈 (Technology Stack)

### 服务端 (Backend)
- **核心框架:** Java 17, Spring Boot 3.2.2, Spring MVC
- **数据持久化:** MyBatis-Plus 3.5.7, MySQL 8.x
- **缓存与搜索引擎:** Redis, Elasticsearch
- **通信与数据流:** WebSocket (即时双向通讯), Spring WebFlux & SSE (单向流式响应)
- **AI 互通:** SDK 原生接入 (Dashscope 等)
- **基础工具:** Hutool, Lombok, Gson, Spring Security

### 桌面端 UI (`reading-ui`)
- **核心框架:** Vue 3.5.x, Vite, Vue Router, Pinia
- **组件及交互:** Element-Plus, Axios

### 移动端 UI (`reading-mobile-ui`)
- **核心框架:** Vue 3.5.x, Vite, Vue Router
- **组件及交互:** Vant 4, `@vant/touch-emulator` (全面适配移动端点触操作逻辑)

## 📁 项目结构 (Project Structure)
```text
reading-system/
├── src/                  # 后端 Java 服务源码及配置文件 (Spring Boot)
├── reading-ui/           # 桌面端 Web 前台应用源码
├── reading-mobile-ui/    # 移动端 H5 混合应用源码
├── pom.xml               # 后端 Maven 依赖配置主文件
├── download_books.py     # 工具库：网络小说/书籍下载爬虫脚本
├── import_books.py       # 工具库：电子书元数据的解析与数据库批量导入脚本
```

## 🚀 快速运行 (Quick Start)

### 1. 环境先决条件
- **Java环境:** JDK 17 及以上
- **Node环境:** Node.js v18 或更高版本
- **中间件依赖:** MySQL 8.x, Redis 缓存服, Elasticsearch 服务

### 2. 后端服务启动
1. 初始化数据库结构，并运行相关的初始化 SQL 文件（如存在）。
2. 在 `src/main/resources/application.yml` 或 `application-dev.yml` 中调整如下参数：
   - MySQL 数据库连接串及账号密码
   - Redis 连接信息
   - Elasticsearch 集群地址
   - AI 大模型平台申请提供的 API Key
3. 经由 IDE 载入 Maven 依赖并启动主启动类，或者在项目根目录下通过终端命令运行：
```bash
mvn clean install
mvn spring-boot:run
```

### 3. 前端应用启动 (桌面端 & 移动端)
项目提供分离式的多端运行载体，根据需要进入对应目录。以桌面端为例：
```bash
cd reading-ui
npm install
npm run dev
```
若需调试移动端环境，可运行：
```bash
cd reading-mobile-ui
npm install
npm run dev
```

