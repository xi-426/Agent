# 架构与数据流

## 1. 系统边界

项目是一个 Spring Boot 单体应用，但内部按职责分为四个业务模块：

- `auth`：注册、登录、BCrypt 密码和 JWT。
- `document`：知识库、文档入库、Embedding、检索、RAG 和评测。
- `chat`：普通/流式对话、会话记忆、历史持久化和限流。
- `todo`：Spring AI 工具和需要确认的待办事项写操作。

`Controller` 只接收 HTTP 参数，`Service` 编排业务，`Repository` 访问数据库。模型不能直接绕过 Service 修改数据库。

## 2. 文档入库链路

```text
JWT 用户
  -> DocumentController
  -> 检查知识库 owner_id
  -> 校验大小、后缀和 MIME Type
  -> 保存原始文件
  -> Apache Tika 提取文字
  -> 清洗文字
  -> 按实验选出的 800 字符、120 重叠切片
  -> Ollama bge-m3 生成 1024 维向量
  -> document_chunk.content + embedding 写入 PostgreSQL
  -> document.status = READY
```

任何中间步骤失败时，文档状态标记为 `FAILED`，并由统一异常处理转换成 JSON 响应。

## 3. RAG 问答链路

```text
问题
  -> 检查知识库所有权
  -> bge-m3 生成问题向量
  -> pgvector 按余弦距离升序取 Top 8
  -> 只保留余弦距离 <= 0.3496 的切片
  -> 拼接带 [资料N] 编号的 Prompt
  -> DeepSeek 根据证据回答或固定拒答
  -> 返回 answer + sources
```

距离阈值是宽松的第一道门；DeepSeek 对“证据能否回答问题”的判断是第二道门。模型固定拒答时，接口返回空 `sources`。

## 4. 会话与 Agent 链路

```text
JWT 用户 + sessionId + 消息
  -> 校验会话属于当前用户
  -> Redis 检查每分钟限流
  -> Redis 读取最近 20 条消息
     -> 缓存为空时从 PostgreSQL 回填
  -> Spring AI 携带历史和 TodoItemTools 调用 DeepSeek
  -> 查询/统计工具可直接读取当前用户待办事项
  -> 创建工具只把内容暂存 Redis
  -> 用户明确回复“确认创建待办”后才写 PostgreSQL
  -> 本轮 USER/ASSISTANT 永久写 PostgreSQL 并追加 Redis
```

`ToolContext` 中的 `userId` 和 `sessionId` 来自服务端 JWT 和已校验会话，不信任模型自己生成的用户编号。

## 5. 数据职责

| 存储 | 保存内容 | 原因 |
|---|---|---|
| PostgreSQL | 用户、知识库、文档、切片、向量、会话历史、待办事项 | 需要长期保存、事务和关联约束 |
| Redis | 最近消息、限流计数、待确认待办事项 | 访问快、允许过期、可从数据库恢复 |
| 文件目录/Volume | 上传的原始文件 | 便于解析和与结构化数据分离 |
| DeepSeek | 当前请求的 Prompt 与回答 | 远程聊天模型，不作为业务数据库 |
| Ollama | 本地 bge-m3 模型 | 本地生成向量，不保存业务记录 |

## 6. 安全边界

- BCrypt 只保存密码哈希。
- 受保护接口从 JWT 读取 `userId`。
- 知识库、会话和待办事项查询都包含用户所有权限制。
- 创建待办事项属于写操作，必须二次确认。
- API 密钥通过环境变量提供，不写入仓库。
- 日志只记录请求元数据和 `requestId`，不记录请求正文或认证头。

## 7. 可观测性与错误流

每个 HTTP 请求先经过 `RequestLoggingFilter`：

```text
生成 requestId
  -> 写入 MDC 和 X-Request-Id 响应头
  -> 执行业务
  -> 记录 method/path/status/durationMs
  -> 清理当前线程 MDC
```

业务异常由 `GlobalExceptionHandler` 映射为 4xx/5xx。JWT 认证失败发生在 Controller 之前，因此由 Spring Security 的入口处理器返回同结构 JSON。

## 8. 评测边界

先使用真实语料长度统计和文档级标签从 17 组方案中选择切片；固定切片后，校准接口再使用精确 chunk 标签的 `CALIBRATION` 数据选择 Top-K 和距离阈值，最后在隔离的 `TEST` 数据上报告：

- `Hit@K`、`Recall@K`、`MRR@K`、`nDCG@K` 和上下文精度。
- `decisionAccuracy`、误接受率和误拒绝率。

它不会批量调用 DeepSeek，以避免把检索指标和生成指标混在一起；答案忠实度、引用正确性等生成层质量需要单独评测。

完整参数来源和实验聚合结果见 [参数证据表](PARAMETER_EVIDENCE.md)。
