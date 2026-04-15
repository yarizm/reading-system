# Lightweight TTS Service

这个服务给阅读系统提供一个更便宜的同步 TTS 接口，当前基于 `edge-tts` 实现。

## 本地运行

```bash
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
uvicorn app:app --host 0.0.0.0 --port 8091
```

## Docker 运行

在项目根目录执行：

```bash
docker compose -f docker-compose.tts.yml up -d --build
```

如果你想看更详细的构建日志，可以改用：

```bash
docker compose -f docker-compose.tts.yml build --no-cache --progress=plain
docker compose -f docker-compose.tts.yml up -d
```

查看状态：

```bash
docker compose -f docker-compose.tts.yml ps
```

停止服务：

```bash
docker compose -f docker-compose.tts.yml down
```

## Endpoints

- `GET /health`
- `POST /synthesize`

请求体：

```json
{
  "text": "你好，欢迎使用轻量 TTS。",
  "voice": "zh-CN-XiaoxiaoNeural"
}
```

返回：

- `audio/mpeg` 音频流

## 与 Spring Boot 的配置关系

- 当前主服务默认会访问 `http://localhost:8091`
- 如果你是在宿主机运行 Spring Boot，并且用 Docker 跑这个 TTS 服务，现有 [application.yml](C:/Users/YARIZM/IdeaProjects/reading-system/src/main/resources/application.yml:55) 不需要改
- 如果后面你把 Spring Boot 也一起放进 Docker，再把 `tts.lightweight.base-url` 改成容器服务名，例如 `http://lightweight-tts:8091`

## 说明

- `voice` 需要传 Edge TTS 支持的 voice id
- 当前容器对外暴露端口 `8091`
- Docker 构建默认已经改为使用清华 PyPI 镜像，国内网络下会更稳定
