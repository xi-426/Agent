# 知屿 · 个人知识库助手

知屿是一个基于 Java 17、Spring Boot、Spring AI、PostgreSQL/pgvector、Redis 与 Docker 构建的个人知识库 Agent。用户可以创建自己的知识库，批量上传资料，基于资料进行带来源问答；当检索证据不足时，系统明确拒答，而不是让模型凭常识补全。

项目还包含带近期记忆的 AI 会话、待办事项查询 Tool Calling，以及需要人工二次确认的待办事项创建流程。

## 核心能力

- 注册登录、JWT 认证和用户资源隔离。
- PDF、DOC、DOCX、TXT、Markdown 单文件或批量上传。
- Apache Tika MIME 检测与正文提取，文本清洗和带重叠的自然边界切片。
- Ollama 运行 `bge-m3`，把文档切片和问题映射为 1024 维向量。
- PostgreSQL/pgvector 保存业务数据与向量，使用余弦距离执行 Top-K 检索。
- 余弦距离 Top-K 检索、距离门控、来源引用和资料不足拒答。
- PostgreSQL 保存完整会话历史，Redis 缓存最近 20 条消息并支持缓存回填。
- Spring AI Tool Calling 查询、统计当前用户待办事项。
- Redis 待确认草稿与完整确认口令控制待办事项写入。
- Redis 用户级会话限流、统一异常响应和 `requestId` 日志关联。
- 校准集/测试集隔离的 RAG 参数搜索与检索质量评测。

## 架构概览

```text
浏览器 / Postman
       |
       v
Spring Security (JWT) -> Controller -> Service
                                  |-> DeepSeek：问答与 Tool Calling
                                  |-> Ollama bge-m3：文本向量
                                  |-> PostgreSQL + pgvector：业务数据、历史、切片和向量
                                  |-> Redis：近期记忆、限流、待确认草稿
                                  |-> 本地目录 / Docker volume：上传的原始文件
```

详细流程见 [架构说明](docs/ARCHITECTURE.md)，参数选择和评测方法见 [RAG 校准与评测](docs/RAG_EVALUATION.md)，每个关键数字的来源见 [参数证据表](docs/PARAMETER_EVIDENCE.md)。

## 当前真实校准结果

2026-08-10 使用本地个人资料库完成了一次真实校准。原始文件和逐题标签属于本地数据，已通过 `.gitignore` 排除，不会上传 GitHub。

- 语料：12 份真实资料，入库后得到 44 个切片和 44 条向量。
- 标注集：48 个问题，其中 36 个有明确来源、12 个在知识库中无答案。
- 数据隔离：32 条只用于选择参数，16 条只用于最终测试。
- 切片实验：基于语料统计构造 17 组方案，校准集选中 `800` 字符与 `120` 字符重叠。
- 检索实验：在选中切片方案上搜索 Top-K 和距离阈值，得到 `Top-K=8`、`maxDistance≈0.3496`；检索结果完全按 pgvector 余弦距离排序，没有自定义重排公式。
- 独立测试集：有来源问题 `Hit@8 = 11/12 = 91.67%`，回答/拒答门控决策 `15/16 = 93.75%`，无答案题误接受 `0/4`。

这些数字只说明当前语料、切片策略与 Embedding 模型下的离线结果，不是 RAG 的通用常数，也不等于生成答案准确率。更换语料、切片方式或 Embedding 模型后应重新校准。

## 技术栈

| 层级 | 技术 |
|---|---|
| 后端 | Java 17、Spring Boot、Spring AI |
| 模型 | DeepSeek Chat API、Ollama `bge-m3` |
| 数据 | PostgreSQL、pgvector、Spring Data JPA、JdbcTemplate、Flyway |
| 缓存 | Redis |
| 安全 | Spring Security、JWT HS256、BCrypt |
| 文档 | Apache Tika |
| 测试 | JUnit 5、AssertJ、Mockito、Spring Boot Test |
| 部署 | Docker、Docker Compose |

## 本地运行

### 1. 配置环境变量

```powershell
Copy-Item .env.example .env
```

在本地 `.env` 中填写自己的值，至少包含：

```dotenv
DEEPSEEK_API_KEY=你的API密钥
POSTGRES_DB=enterprise_agent
POSTGRES_USER=agent
POSTGRES_PASSWORD=本地数据库密码
JWT_SECRET=至少32字节的随机字符串
```

`.env` 不会提交到 Git。不要把真实密钥写入 `application.yml`、截图或聊天记录。

### 2. 启动基础设施

```powershell
docker compose up -d
docker compose exec ollama ollama pull bge-m3
```

PostgreSQL、Redis 和 Ollama 数据保存在 Docker named volume 中；普通地删除、重建容器不会删除这些数据。

### 3. 启动应用

在 VS Code 中打开 [EnterpriseAgentApplication.java](src/main/java/com/yan/agent/EnterpriseAgentApplication.java)，配置同名进程环境变量后点击 `Run`。也可以运行：

```powershell
.\mvnw.cmd spring-boot:run
```

- 应用页面：<http://localhost:8080>
- 健康检查：<http://localhost:8080/actuator/health>

### 4. 运行测试

可以在 VS Code Testing 面板点击 `Run Tests`，也可以运行：

```powershell
.\mvnw.cmd test
```

## Docker 中运行应用

默认 `docker compose up -d` 只启动依赖服务。把 Java 应用也放入 Docker：

```powershell
docker compose --profile application up -d --build
```

如果本机已有被 Git 忽略的 `compose.local.yaml`（用于保存本机专用配置），应叠加启动：

```powershell
docker compose -f compose.yaml -f compose.local.yaml --profile application up -d --build
```

## 主要 API

| 方法 | 路径 | 认证 | 作用 |
|---|---|---|---|
| POST | `/api/v1/auth/register` | 否 | 注册并返回 JWT |
| POST | `/api/v1/auth/login` | 否 | 登录并返回 JWT |
| GET/POST | `/api/v1/chat/sessions` | 是 | 查询或创建会话 |
| POST | `/api/v1/chat/sessions/{sessionId}` | 是 | 带记忆和待办事项工具的会话 |
| GET/POST | `/api/v1/knowledge-bases` | 是 | 查询或创建知识库 |
| GET | `/api/v1/knowledge-bases/{id}/documents` | 是 | 查询文档 |
| POST | `/api/v1/knowledge-bases/{id}/documents` | 是 | 上传单个文档 |
| POST | `/api/v1/knowledge-bases/{id}/documents/batch` | 是 | 批量上传文档 |
| POST | `/api/v1/knowledge-bases/{id}/rag/ask` | 是 | 带来源的 RAG 问答 |
| POST | `/api/v1/knowledge-bases/{id}/evaluations/calibration` | 是 | 校准参数并评测检索链路 |

浏览器工作台会自动携带登录返回的 JWT。Postman 调用受保护接口时，需要在 `Authorization` 中使用 Bearer Token。

## 统一错误响应

```json
{
  "status": 401,
  "message": "请先登录或提供有效的访问令牌",
  "timestamp": "2026-08-10T10:00:00Z",
  "requestId": "37f64e1d-..."
}
```

响应头同时包含 `X-Request-Id`。日志记录请求方法、路径、状态码与耗时，不记录密码、Token 或请求正文。
