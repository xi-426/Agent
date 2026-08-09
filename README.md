# 企业知识库智能 Agent

基于 Java 17、Spring Boot、Spring AI、PostgreSQL/pgvector、Redis 和 Docker 构建的企业知识库与工单 Agent。项目覆盖文档入库、向量检索、RAG 引用回答、会话记忆、Tool Calling、JWT 用户隔离、限流、评测和容器化部署。

> 本项目使用的公司制度与业务数据均为虚构测试资料，不代表任何现实企业，也不构成法律、合规或管理建议。

## 核心能力

- 普通对话与 SSE 流式输出。
- 微信桌面版风格的响应式工作台，集成注册登录、会话记忆、工单 Tool Calling、知识库、文档上传、RAG 问答和固定评测。
- PDF、DOCX、TXT、Markdown 文档校验、保存、解析、清洗和重叠切片。
- Ollama `bge-m3` 本地 Embedding，PostgreSQL `pgvector` HNSW 相似度检索。
- 候选召回、距离过滤、混合重排、资料不足拒答和来源引用。
- Redis 最近消息缓存，PostgreSQL 永久会话历史与缓存回填。
- Spring AI Tool Calling：按用户查询、统计和二次确认创建工单。
- BCrypt 密码、JWT 认证、知识库/会话/工单用户隔离和 Redis 限流。
- 固定 RAG 评测集、请求追踪编号、统一异常响应和 Actuator 健康检查。

## 架构概览

```text
浏览器 / Postman
       |
       v
Spring Security (JWT) -> Controller -> Service
                                  |-> DeepSeek：回答与 Tool Calling
                                  |-> Ollama bge-m3：文本向量
                                  |-> PostgreSQL + pgvector：业务数据、历史、向量
                                  |-> Redis：短期记忆、限流、待确认动作
                                  |-> 本地/Volume：上传的原始文件
```

详细流程见 [架构说明](docs/ARCHITECTURE.md)。

## 技术栈

| 层级 | 技术 |
|---|---|
| 后端 | Java 17、Spring Boot 4.1、Spring AI 2.0 |
| 模型 | DeepSeek Chat API、Ollama `bge-m3` |
| 数据 | PostgreSQL 17、pgvector、Spring Data JPA、JdbcTemplate、Flyway |
| 缓存 | Redis 8 |
| 安全 | Spring Security、JWT HS256、BCrypt |
| 文档 | Apache Tika |
| 测试 | JUnit 5、AssertJ、Mockito、Spring Boot Test |
| 部署 | Docker、Docker Compose |

## 本地运行

### 1. 准备环境变量

在项目根目录创建不会提交到 Git 的 `.env`：

```dotenv
DEEPSEEK_API_KEY=你的API密钥
JWT_SECRET=至少32字节且仅供当前环境使用的随机字符串
```

不要把真实密钥写进 `application.yml`、截图、聊天或 Git。

### 2. 启动基础设施

```powershell
docker compose up -d
docker compose exec ollama ollama pull bge-m3
```

PostgreSQL、Redis 和 Ollama 数据分别保存在 Docker named volume 中，删除普通容器不会删除数据。

### 3. 在 VS Code 启动 Java 应用

打开 [EnterpriseAgentApplication.java](src/main/java/com/yan/agent/EnterpriseAgentApplication.java)，点击 `Run`。

可视化检查：

- 页面：<http://localhost:8080>
- 健康状态：<http://localhost:8080/actuator/health>
- PostgreSQL：VS Code 数据库插件、DBeaver 或 pgAdmin
- Redis：VS Code Redis 插件

### 4. 运行全部测试

在 VS Code Testing 面板选择 `Run Tests`，或者运行：

```powershell
.\mvnw.cmd test
```

当前完整结果：21 个测试，0 失败，0 错误。

## 完整 Docker 运行

默认 `docker compose up -d` 只启动基础设施，不会占用 Java 应用的 8080 端口。

需要把 Java 应用也放入 Docker 时：

```powershell
docker compose --profile application up -d --build
```

应用镜像使用非 root 用户运行，上传文件保存在 `app_uploads` volume。首次构建需要下载 Maven 和 Java 基础镜像，因此耗时较长。

## 主要 API

| 方法 | 路径 | 认证 | 作用 |
|---|---|---|---|
| POST | `/api/v1/auth/register` | 否 | 注册并返回 JWT |
| POST | `/api/v1/auth/login` | 否 | 登录并返回 JWT |
| POST | `/api/v1/chat` | 否 | 普通同步对话 |
| POST | `/api/v1/chat/stream` | 否 | SSE 流式对话 |
| GET | `/api/v1/chat/sessions` | 是 | 列出当前用户会话 |
| POST | `/api/v1/chat/sessions` | 是 | 创建用户会话 |
| GET | `/api/v1/chat/sessions/{sessionId}/messages` | 是 | 读取最近会话历史 |
| POST | `/api/v1/chat/sessions/{sessionId}` | 是 | 带记忆和工单工具的对话 |
| GET | `/api/v1/knowledge-bases` | 是 | 列出当前用户知识库 |
| POST | `/api/v1/knowledge-bases` | 是 | 创建知识库 |
| GET | `/api/v1/knowledge-bases/{id}/documents` | 是 | 列出知识库文档 |
| POST | `/api/v1/knowledge-bases/{id}/documents` | 是 | 上传、解析、切片并向量化文档 |
| POST | `/api/v1/knowledge-bases/{id}/rag/ask` | 是 | 有引用的 RAG 问答 |
| POST | `/api/v1/knowledge-bases/{id}/evaluations/retrieval` | 是 | 批量检索评测 |

浏览器工作台会自动携带登录返回的 JWT。使用 Postman 时，在 `Authorization` 中选择 `Bearer Token`，填入注册或登录返回的 `accessToken`。

## 统一错误响应

```json
{
  "status": 401,
  "message": "请先登录或提供有效的访问令牌",
  "timestamp": "2026-07-22T10:00:00Z",
  "requestId": "37f64e1d-..."
}
```

响应头也包含 `X-Request-Id`。后端日志会记录同一个编号以及请求方法、路径、状态码和耗时，但不会记录密码、Token 或请求正文。

## 项目资料

- [架构与数据流](docs/ARCHITECTURE.md)
- [Postman 演示脚本](docs/DEMO_GUIDE.md)
- [RAG 固定评测集](docs/RAG_EVALUATION.md)
- [简历描述与面试复盘](docs/RESUME_AND_INTERVIEW.md)
- [7 日学习计划](docs/LEARNING_PLAN.md)
- [每日总结](docs/DAILY_SUMMARY.md)
- [错误记录](docs/ERROR_LOG.md)
- [项目交接状态](docs/PROJECT_CONTEXT.md)

## 已知边界

- 当前是单体应用，适合学习、作品演示和小规模内部验证，不等同于生产级多租户 SaaS。
- RAG 使用固定阈值和可解释的轻量混合重排；上线前应使用真实业务数据持续评测。
- 原始文件保存在本地目录或 Docker volume，生产环境可替换为对象存储。
- JWT 使用单一 HS256 密钥，生产环境应接入专用密钥管理、轮换和撤销机制。
- 演示前端把 JWT 保存在浏览器 `localStorage`；正式生产环境应结合威胁模型改用 HttpOnly Cookie/BFF，并完善 CSP 与 XSS 防护。
