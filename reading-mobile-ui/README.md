# 智能阅读系统移动端

`reading-mobile-ui` 是智能阅读系统的移动端前端，基于 Vue 3、Vite、Vue Router 和 Vant 构建。

## 技术栈

- Vue 3
- Vite
- Vue Router
- Vant
- Pinia
- Axios
- marked + dompurify（Markdown 渲染 + XSS 防护）
- IndexedDB（图片本地缓存）

## 环境要求

建议使用 Node.js 22，与仓库 CI 保持一致。

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
http://localhost:5174
```

如果端口被占用，Vite 会自动分配其他端口，请以终端输出为准。

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

当前移动端没有使用 `VITE_API_BASE_URL` 等前端环境变量。开发和预览环境通过 `vite.config.js` 中的 proxy 访问后端：

- `/api` 转发到 `http://localhost:8090`
- `/ws` 转发到 `http://localhost:8090`
- `/files` 转发到 `http://localhost:8090`

如后端端口或地址变化，请同步调整 `vite.config.js` 中的 proxy `target`。

## 移动端调试

- 开发服务默认监听 `0.0.0.0`，方便同一局域网内手机访问。
- 手机访问前请确认电脑和手机处于同一网络。
- 访问地址以 Vite 终端输出的 Network URL 为准。
- 浏览器调试时建议开启设备模拟模式检查移动端布局。

## 启动依赖

移动端页面依赖后端 API。启动前请确认：

- Spring Boot 后端已运行在 `8090`
- 后端需要的 MySQL、Redis、Elasticsearch 已按功能需要启动
- 如需听书功能，TTS 服务已运行在 `8091`
