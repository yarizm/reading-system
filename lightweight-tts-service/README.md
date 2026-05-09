# 轻量 TTS 服务

`lightweight-tts-service` 为智能阅读系统提供轻量文本转语音能力，基于 FastAPI、uvicorn 和 edge-tts 实现。后端默认通过该服务生成段落朗读和整章听书音频。

## 技术栈

- FastAPI
- uvicorn
- edge-tts

## 安装依赖

```bash
python -m venv .venv
.venv\Scripts\activate
python -m pip install -r requirements.txt
```

## 本地启动

```bash
python -m uvicorn app:app --host 0.0.0.0 --port 8091
```

健康检查：

```text
GET http://localhost:8091/health
```

## Docker 启动

在项目根目录执行：

```bash
docker compose -f docker-compose.tts.yml up -d --build
```

查看状态：

```bash
docker compose -f docker-compose.tts.yml ps
```

停止服务：

```bash
docker compose -f docker-compose.tts.yml down
```

## 默认接口

### `POST /synthesize`

请求体：

```json
{
  "text": "你好，欢迎使用轻量 TTS。",
  "voice": "zh-CN-XiaoxiaoNeural"
}
```

返回：

```text
audio/mpeg
```

`voice` 需要使用 Edge TTS 支持的 voice id。不传时默认使用 `zh-CN-XiaoxiaoNeural`。

## 后端调用方式

Spring Boot 后端默认读取以下配置：

```yaml
tts:
  provider: lightweight
  lightweight:
    base-url: "http://localhost:8091"
```

当后端在宿主机运行、TTS 服务通过 Docker 运行时，默认配置无需调整。如果后端也运行在 Docker 网络内，需要将 `tts.lightweight.base-url` 改为容器服务名，例如：

```yaml
tts:
  lightweight:
    base-url: "http://lightweight-tts:8091"
```

## 常见问题

### 端口被占用

默认端口为 `8091`。如需调整端口，请同时修改启动命令、Docker Compose 端口映射和后端 `tts.lightweight.base-url`。

### 生成音频失败

- 检查网络是否能访问 edge-tts 所需服务
- 检查请求文本是否为空
- 检查 `voice` 是否为有效的 Edge TTS voice id

### 后端无法调用

- 检查 `GET /health` 是否返回正常
- 检查后端配置的 `tts.lightweight.base-url`
- 检查防火墙或 Docker 端口映射
