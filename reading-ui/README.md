# 智能阅读系统桌面端

`reading-ui` 是智能阅读系统的桌面端前端，基于 Vue 3、Vite、Vue Router 和 Element Plus 构建。

## 技术栈

- Vue 3
- Vite
- Vue Router
- Element Plus
- Axios
- Pinia

## 环境要求

桌面端 `package.json` 当前要求 Node.js 版本满足：

```text
^20.19.0 || >=22.12.0
```

建议本地和 CI 使用 Node.js 22。

## 安装依赖

```sh
npm install
```

推荐在 CI 或干净环境中使用：

```sh
npm ci
```

## 本地开发

```sh
npm run dev
```

默认开发地址通常为：

```text
http://localhost:5173
```

## 构建

```sh
npm run build
```

构建产物输出到 `dist`。

## 预览

```sh
npm run preview
```

## 后端 API 地址配置

当前桌面端没有使用 `VITE_API_BASE_URL` 等前端环境变量。开发环境通过 `vite.config.js` 中的 proxy 访问后端：

- `/api` 转发到 `http://localhost:8090`
- `/ws` 转发到 `http://localhost:8090`
- `/files` 转发到 `http://localhost:8090`

如后端端口或地址变化，请同步调整 `vite.config.js` 中的 proxy `target`。

## 启动依赖

桌面端页面依赖后端 API。启动前请确认：

- Spring Boot 后端已运行在 `8090`
- 后端需要的 MySQL、Redis、Elasticsearch 已按功能需要启动
- 如需听书功能，TTS 服务已运行在 `8091`
