# 智能阅读系统移动端

`reading-mobile-ui` 是智能阅读系统的移动端前端，基于 Vue 3、Vite、Vue Router 和 Vant 构建。

## 技术栈

- Vue 3
- Vite
- Vue Router
- Vant
- Axios

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

开发和预览环境代理配置位于 `vite.config.js`：

- `/api` 转发到 `http://localhost:8090`
- `/ws` 转发到 `http://localhost:8090`
- `/files` 转发到 `http://localhost:8090`

如后端端口或地址变化，请同步调整 `vite.config.js` 中的 `target`。

## 移动端调试

- 开发服务默认监听 `0.0.0.0`，方便同一局域网内手机访问。
- 手机访问前请确认电脑和手机处于同一网络。
- 访问地址以 Vite 终端输出的 Network URL 为准。
- 浏览器调试时建议开启设备模拟模式检查移动端布局。
