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
- Spring AI Tool Calling：使用服务端可信用户身份查询和统计当前用户工单。
- 工单创建由 Java 解析信息并暂存 Redis，用户输入完整确认口令后才写入 PostgreSQL。
- BCrypt 密码、JWT 认证、知识库/会话/工单用户隔离，以及登录后 Agent 会话的 Redis 用户限流。
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

复制公开模板，在项目根目录创建不会提交到 Git 的 `.env`：

```powershell
Copy-Item .env.example .env
```

替换其中的占位值，至少需要配置：

```dotenv
DEEPSEEK_API_KEY=你的API密钥
POSTGRES_DB=enterprise_agent
POSTGRES_USER=agent
POSTGRES_PASSWORD=仅供本地开发使用的数据库密码
JWT_SECRET=至少32字节且仅供当前环境使用的随机字符串
```

Docker Compose 会自动读取根目录 `.env`。Spring Boot 从 IDE 或 Maven 单独启动时不会自动读取该文件，必须通过进程环境变量或 IDE 启动配置传入相同值。

不要把真实密钥写进 `application.yml`、截图、聊天、IDE共享配置或 Git。

### 2. 启动基础设施

```powershell
docker compose up -d
docker compose exec ollama ollama pull bge-m3
```

PostgreSQL、Redis 和 Ollama 数据分别保存在 Docker named volume 中，删除普通容器不会删除数据。

### 3. 在本机启动 Java 应用

如果希望从 VS Code 点击 `Run`，请先在个人启动配置中设置以下环境变量，且不要提交该私人配置：

- `DEEPSEEK_API_KEY`
- `JWT_SECRET`
- `POSTGRES_DB`
- `POSTGRES_USER`
- `POSTGRES_PASSWORD`

这些数据库配置必须与 `.env` 中供 Docker Compose 使用的值一致。然后打开 [EnterpriseAgentApplication.java](src/main/java/com/yan/agent/EnterpriseAgentApplication.java)，点击 `Run`。

也可以在已设置相同进程环境变量的 PowerShell 中运行：

```powershell
.\mvnw.cmd spring-boot:run
```

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

当前源码包含 24 个 `@Test` 测试方法。仓库最近一次保留的 Surefire 测试报告生成于 2026-07-23，结果为 24 个测试、0 失败、0 错误；提交或部署前应在当前环境重新运行确认。

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
