# 企业知识库智能 Agent

这是一个边学习边实现的Java AI项目。当前完成到**第1课：普通模型对话**。

## 当前数据流

```text
浏览器
  -> POST /api/v1/chat
  -> ChatController
  -> AiChatService
  -> Spring AI ChatClient
  -> DeepSeek API
  -> JSON答案
  -> 浏览器展示
```

当前还没有接入知识库、RAG、工单工具、数据库和流式输出。它们会在理解当前链路后逐步加入。

## 环境要求

- Java 17
- Maven 3.9.9，或者项目生成后的Maven Wrapper
- 一个有效的DeepSeek API Key

## 启动

在PowerShell中执行：

```powershell
cd D:\yan\Agent
$env:DEEPSEEK_API_KEY="替换成你的真实Key"
.\mvnw.cmd spring-boot:run
```

启动后访问：

- 页面：<http://localhost:8080>
- 健康检查：<http://localhost:8080/actuator/health>

不要把真实Key写入`application.yml`或提交到Git。

## API测试

```powershell
$body = @{ message = "请用三句话解释什么是RAG" } | ConvertTo-Json
Invoke-RestMethod `
  -Method Post `
  -Uri http://localhost:8080/api/v1/chat `
  -ContentType "application/json" `
  -Body $body
```

## 教学资料

- [第1课详细讲义](docs/lesson-01-chat.md)
- [项目路线图](docs/project-roadmap.md)

