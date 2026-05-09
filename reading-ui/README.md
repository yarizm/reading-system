# 智能阅读系统桌面端

`reading-ui` 是智能阅读系统的桌面端前端，基于 Vue 3、Vite、Vue Router 和 Element Plus 构建。

## 技术栈

- Vue 3
- Vite
- Vue Router
- Element Plus
- Axios
- Pinia

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

开发环境代理配置位于 `vite.config.js`：

- `/api` 转发到 `http://localhost:8090`
- `/ws` 转发到 `http://localhost:8090`
- `/files` 转发到 `http://localhost:8090`

如后端端口或地址变化，请同步调整 `vite.config.js` 中的 `target`。
