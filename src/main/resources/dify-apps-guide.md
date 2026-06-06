# Dify 应用编排构建清单

本文档列出智能阅读系统在 Dify 侧需要创建和配置的全部应用。每个应用包含类型、输入变量、输出格式和构建要点。

---

## 总览

| # | 应用名称 | 应用类型 | 调用方式 | 环境变量 Key | 对应后端组件 |
|---|---------|---------|---------|-------------|-------------|
| 1 | 阅读助手 | Chat Assistant | SSE 流式 | `DIFY_READING_KEY` | `DifyAiController` |
| 2 | 书籍推荐 | Chat Assistant | 阻塞 | `DIFY_RECOMMEND_KEY` | `BookRecommendationServiceImpl` |
| 3 | 系统向导 | Chat Assistant | SSE 流式 | `DIFY_GUIDE_KEY` | `GuideController` |
| 4 | 笔记 AI | Workflow | 阻塞 | `DIFY_NOTE_KEY` | `NoteAiController`, `SocialAiController` |
| 5 | 阅读洞察 | Workflow | 阻塞 | `DIFY_INSIGHT_KEY` | `InsightController` |
| 6 | 知识库 | Dataset | CRUD API | `DIFY_KB_KEY` | `DifyKnowledgeBaseService` |

**通用配置**：
- `DIFY_BASE_URL` — Dify API 基础地址（如 `https://api.dify.ai/v1`）
- Chat 类应用使用 `/chat-messages` 端点
- Workflow 类应用使用 `/workflows/run` 端点
- Dataset API 使用 `/datasets` 端点

---

## 1. 阅读助手（Chat Assistant）

**用途**：用户在阅读时选中文本，进行解读、批判、联想等分析。

**应用类型**：Chat Assistant（非 Workflow）

**环境变量**：
```
DIFY_CHAT_URL=<dify-api-url>
DIFY_READING_KEY=<app-api-key>
```

**输入变量**（App Variables）：

| 变量名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `selected_text` | String | 是 | 用户选中的文本 |
| `custom_mode` | String | 是 | 分析模式（解读/批判/联想等） |
| `book_name` | String | 否 | 当前书名，默认"未知书籍" |
| `book_id` | String | 否 | 当前书籍 ID |
| `reading_progress` | String | 否 | 阅读进度百分比 |
| `recent_notes` | String | 否 | 最近的笔记内容（上下文注入） |

**Query 字段**：`custom_mode` 值，前缀拼接书名。示例：`关于《三体》：解读这段文本`

**对话管理**：支持 `conversation_id` 传入以维持多轮对话。

**输出**：SSE 流式文本，直接转发给前端。

**构建要点**：
- 模型建议选择支持长上下文的模型（如 GPT-4o、Claude Sonnet）
- System Prompt 应包含：分析模式说明、输出格式要求、中文回复
- 可在 Dify 的「上下文」功能中关联知识库（Dataset），让助手能引用书籍原文

---

## 2. 书籍推荐（Chat Assistant）

**用途**：根据用户的书架和协同过滤数据，生成首页个性化书籍推荐。

**应用类型**：Chat Assistant（非 Workflow）

**环境变量**：
```
DIFY_CHAT_URL=<dify-api-url>
DIFY_RECOMMEND_KEY=<app-api-key>
```

**输入变量**（App Variables）：

| 变量名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `scene` | String | 是 | 推荐场景，固定值 `homepage_book_recommendation` |
| `target_user_books` | String (JSON) | 是 | 用户已读书籍列表 |
| `collaborative_candidates` | String (JSON) | 是 | 协同过滤候选书籍 |
| `all_books` | String (JSON | 是 | 全量书籍列表（候选池） |
| `recommend_count` | Number | 是 | 推荐数量 |

**Query 字段**：`Generate homepage book recommendations from the provided inputs and return JSON only.`

**输出格式**：返回严格 JSON 数组，每个元素包含 `bookId`（数字）和 `reason`（推荐理由字符串）。示例：
```json
[
  {"bookId": 42, "reason": "与您读过的《三体》同为硬科幻"},
  {"bookId": 17, "reason": "您对历史类书籍有较高阅读完成率"}
]
```

**构建要点**：
- System Prompt 必须强调「只返回 JSON 数组，不要包含任何其他文本」
- 后端有熔断机制：连续失败会临时降级为随机推荐
- 建议设置 Temperature 为 0.7 以保证推荐多样性
- 推荐结果通过 `bookId` 与本地书籍库匹配，`bookId` 必须是 `all_books` 中存在的 ID

---

## 3. 系统向导（Chat Assistant）

**用途**：回答用户关于系统功能的提问，提供阅读统计问答和功能引导。

**应用类型**：Chat Assistant（非 Workflow）

**环境变量**：
```
DIFY_CHAT_URL=<dify-api-url>
DIFY_GUIDE_KEY=<app-api-key>
```

**输入变量**（App Variables）：

| 变量名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `books_reading_count` | String | 是 | 正在阅读的书籍数量 |
| `books_finished_count` | String | 是 | 已完成阅读的书籍数量 |
| `total_notes_count` | String | 是 | 笔记总数 |
| `last_read_book_title` | String | 否 | 最近阅读的书名 |
| `system_features` | String | 否 | 系统功能列表描述 |

**Query 字段**：用户输入的问题。

**对话管理**：支持 `conversation_id` 传入以维持多轮对话。

**输出**：SSE 流式文本，直接转发给前端。

**构建要点**：
- System Prompt 应定义角色为「智能阅读系统助手」，熟悉所有功能
- 注入的统计变量让助手能回答「我读了多少本书」「我有多少笔记」等个性化问题
- 功能列表变量让助手能引导用户发现新功能

---

## 4. 笔记 AI（Workflow）

**用途**：对用户笔记执行 AI 操作——润色扩展、生成摘要、生成测验题。

**应用类型**：Workflow（非 Chat Assistant）

**环境变量**：
```
DIFY_WORKFLOW_URL=<dify-api-url>
DIFY_NOTE_KEY=<workflow-app-api-key>
```

**输入变量**（Workflow Inputs）：

| 变量名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `task_type` | String | 是 | 固定值 `note` |
| `action` | String | 是 | 操作类型：`enhance` / `summarize` / `quiz` |
| `notes_context` | String | 是 | 笔记内容（enhance 时为单条笔记，summarize/quiz 时为全书笔记） |
| `book_info` | String | 否 | 书籍信息（`书名 - 作者`） |
| `context` | String | 否 | 附加上下文（如笔记标题） |

**输出字段**（Workflow Outputs）：

| 变量名 | 类型 | 说明 |
|--------|------|------|
| `result` | String | AI 生成的结果文本 |

**三种 action 的 Workflow 编排建议**：

### enhance（润色/扩展）
- **输入**：单条笔记内容
- **处理**：LLM 节点扩展和润色笔记，补充论据、修正表述
- **输出**：扩展后的笔记全文

### summarize（摘要）
- **输入**：全书所有笔记（拼接字符串）
- **处理**：LLM 节点提炼核心观点、关键启发、待深入问题
- **输出**：结构化摘要

### quiz（测验）
- **输入**：全书所有笔记
- **处理**：LLM 节点生成 3-5 道测验题（选择题 + 简答题）
- **输出**：测验题列表

**构建要点**：
- **必须是 Workflow 类型**，不是 Chat Assistant。Chat Assistant 调用 `/workflows/run` 会返回 `not_workflow_app` 错误
- Workflow 起始节点接收上述 Inputs
- 使用 IF/ELSE 节点根据 `action` 值分支到不同 LLM 节点
- 每个分支的 LLM 节点输出汇聚到一个 `result` 输出变量
- 后端通过 `data.outputs.result` 读取结果

---

## 5. 阅读洞察（Workflow）

**用途**：分析用户的阅读数据，生成个性化的阅读洞察报告。

**应用类型**：Workflow（非 Chat Assistant）

**环境变量**：
```
DIFY_WORKFLOW_URL=<dify-api-url>
DIFY_INSIGHT_KEY=<workflow-app-api-key>
```

**输入变量**（Workflow Inputs）：

| 变量名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `reading_stats` | String (JSON) | 是 | 用户阅读统计数据 |

`reading_stats` JSON 结构示例：
```json
{
  "totalBooks": 15,
  "readingBooks": 3,
  "finishedBooks": 12,
  "totalNotes": 48,
  "recentBooks": [
    {"title": "三体", "author": "刘慈欣", "progress": 85},
    {"title": "人类简史", "author": "尤瓦尔·赫拉利", "progress": 100}
  ],
  "topTags": ["科幻", "历史", "心理学"],
  "readingDays": 23,
  "avgNotesPerBook": 4
}
```

**输出字段**（Workflow Outputs）：

| 变量名 | 类型 | 说明 |
|--------|------|------|
| `report` | String | Markdown 格式的洞察报告 |

**构建要点**：
- LLM 节点接收 `reading_stats` JSON，生成结构化报告
- 报告应包含：阅读概况、阅读习惯分析、知识覆盖领域、改进建议
- 输出为 Markdown 格式，前端通过 `marked` + `DOMPurify` 渲染
- 报告长度建议 500-1000 字

---

## 6. 知识库（Dataset）

**用途**：将书籍章节同步到 Dify 知识库，供阅读助手检索引用。

**类型**：Dify Dataset API（非 Chat/Workflow 应用）

**环境变量**：
```
DIFY_KB_URL=<dify-api-url>
DIFY_KB_KEY=<dataset-api-key>
DIFY_KB_DATASET_ID=<dataset-uuid>
```

**API 操作**：

| 操作 | 端点 | 说明 |
|------|------|------|
| 创建文档 | `POST /datasets/{id}/documents/create-by-text` | 新增章节文档 |
| 更新文档 | `POST /datasets/{id}/documents/{doc_id}/update-by-text` | 更新已有章节 |
| 删除文档 | `DELETE /datasets/{id}/documents/{doc_id}` | 删除章节文档 |

**文档结构**：
- 每本书的每个章节创建为一个独立文档
- 文档名称格式：`{书名} - 第{N}章 {章节标题}`
- 文档内容：章节正文纯文本

**同步策略**：
- `@Async("kbSyncExecutor")` 异步执行
- 逐章节顺序处理，每章间隔 200ms（`dify.kb.chapter-delay-ms`）
- 新章节（无 `kbDocumentId`）→ 创建文档
- 已有章节 → 更新文档
- 书籍下架（`status=4`）→ 删除所有章节文档
- 记录 `hasFailures` 状态，完成后输出聚合日志

**构建要点**：
- 在 Dify 后台创建一个空 Dataset，获取 Dataset ID
- 推荐使用自动分段（Automatic），Dify 会按段落/长度自动切分
- 如果书籍章节较长（>5000 字），建议在后端预处理分段
- Dataset 关联到阅读助手应用后，助手可以检索并引用书籍原文

---

## 环境变量完整清单

```bash
# Dify 基础地址
DIFY_BASE_URL=https://api.dify.ai/v1

# Chat 类应用（3 个）
DIFY_CHAT_URL=https://api.dify.ai/v1        # 通常与 DIFY_BASE_URL 相同
DIFY_READING_KEY=app-xxxx                    # 阅读助手
DIFY_RECOMMEND_KEY=app-xxxx                  # 书籍推荐
DIFY_GUIDE_KEY=app-xxxx                      # 系统向导

# Workflow 类应用（2 个）
DIFY_WORKFLOW_URL=https://api.dify.ai/v1     # 通常与 DIFY_BASE_URL 相同
DIFY_NOTE_KEY=app-xxxx                       # 笔记 AI
DIFY_INSIGHT_KEY=app-xxxx                    # 阅读洞察

# Dataset（1 个）
DIFY_KB_URL=https://api.dify.ai/v1          # 通常与 DIFY_BASE_URL 相同
DIFY_KB_KEY=dataset-xxxx                     # Dataset API Key
DIFY_KB_DATASET_ID=uuid                      # Dataset ID
```

## 构建顺序建议

1. **知识库** → 先创建 Dataset，获取 ID 和 API Key
2. **阅读助手** → 创建 Chat Assistant，关联知识库
3. **笔记 AI** → 创建 Workflow，配置三个 action 分支
4. **阅读洞察** → 创建 Workflow，配置报告生成节点
5. **书籍推荐** → 创建 Chat Assistant，配置 JSON 输出约束
6. **系统向导** → 创建 Chat Assistant，注入系统功能描述

## 常见问题

### Workflow 调用返回 `not_workflow_app`
确认在 Dify 后台创建的是 **Workflow** 类型应用，不是 Chat Assistant。Chat Assistant 不支持 `/workflows/run` 端点。

### Chat 应用调用返回 401
检查 API Key 是否正确。每个应用有独立的 API Key，在 Dify 应用的「访问 API」页面获取。

### 知识库同步后阅读助手无法检索
确认 Dataset 已关联到阅读助手应用：Dify 应用 → 上下文 → 添加知识库 → 选择对应 Dataset。

### 推荐结果 JSON 解析失败
检查 Dify 应用的 System Prompt 是否明确要求「只返回 JSON 数组」。可在 Dify 的「模型配置」中设置 `Response Format` 为 JSON。
