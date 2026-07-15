# 第1课：从浏览器到DeepSeek的普通对话

## 1. 本课目标

本课只解决一个问题：用户在网页输入一句话，Java后端把它发给DeepSeek，再把完整答案返回网页。

先理解普通对话，后面才能真正理解流式输出、RAG和Agent到底增加了什么。

## 2. 整体数据流

```text
用户输入问题
  ↓
app.js发送HTTP POST请求
  ↓
ChatController接收JSON
  ↓
AiChatService处理业务
  ↓
ChatClient调用DeepSeek
  ↓
DeepSeek返回完整答案
  ↓
Controller包装成JSON
  ↓
网页展示答案
```

每层只做自己的事：

- Controller负责HTTP，不负责研究模型怎么调用。
- Service负责业务流程，不负责操作HTML。
- ChatClient负责把Java调用转换为模型API请求。
- DTO负责描述请求或响应的数据形状。

## 3. 为什么先不用record

`ChatRequest`当前是普通Java类：字段、无参构造器、构造器、getter和setter全部明确写出。

这样你能直接看到Jackson接收JSON时做了什么：

1. 调用无参构造器创建`ChatRequest`对象。
2. 读取JSON中的`message`。
3. 调用`setMessage`保存值。
4. Controller调用`getMessage`读取值。

以后可以把DTO改成：

```java
public record ChatRequest(String message) {
}
```

但必须先理解上面被省略掉的普通写法。`record`适合只承载数据的对象，不适合需要频繁修改状态的实体。

## 4. 注解在这里做了什么

### `@SpringBootApplication`

它标记程序入口，并让Spring扫描`com.yan.agent`下面的组件。

### `@RestController`

告诉Spring：这个类接收HTTP请求，方法返回值要转换成JSON。

### `@RequestMapping`和`@PostMapping`

二者组合出接口地址：

```text
/api/v1/chat + POST = POST /api/v1/chat
```

### `@Service`

告诉Spring创建一个`AiChatService`对象并管理它。Controller不需要自己`new AiChatService()`。

### `@Configuration`和`@Bean`

`AiClientConfig`负责创建`ChatClient`。`@Bean`方法的返回对象会交给Spring容器，其他类可以通过构造器获得它。

### `@Valid`、`@NotBlank`和`@Size`

它们在进入Controller方法前检查参数。空消息或超过4000字符时，Spring抛出校验异常，由`GlobalExceptionHandler`转换成容易理解的JSON错误。

## 5. 什么是依赖注入

`ChatController`需要`AiChatService`：

```java
public ChatController(AiChatService chatService) {
    this.chatService = chatService;
}
```

这里没有手工`new`。Spring启动时发现：

1. `ChatController`是一个组件。
2. 它的构造器需要`AiChatService`。
3. `AiChatService`有`@Service`，Spring已经创建了它。
4. Spring把这个对象传入Controller构造器。

这叫构造器依赖注入。优点是依赖关系明确，也方便测试时替换成模拟对象。

## 6. 为什么拆开ChatClient链式调用

`AiChatService`没有直接写成一长串：

```java
return chatClient.prompt().user(userMessage).call().content();
```

而是拆成：

```java
ChatClient.ChatClientRequestSpec request = chatClient
        .prompt()
        .user(userMessage);

ChatClient.CallResponseSpec response = request.call();
return response.content();
```

三步含义分别是：

1. 创建一次模型请求并放入用户消息。
2. `call()`真正发出网络请求，并等待完整响应。
3. `content()`从模型响应中取出文本。

后续理解熟练后，可以再决定是否合并。

## 7. 请求和响应长什么样

请求：

```json
{
  "message": "请解释什么是RAG"
}
```

正常响应：

```json
{
  "answer": "RAG是……"
}
```

没有配置Key时：

```json
{
  "status": 503,
  "message": "尚未配置DEEPSEEK_API_KEY，请先阅读README中的启动步骤",
  "timestamp": "2026-07-15T...Z"
}
```

## 8. 常见错误与排查

### 构建失败但还没有编译Java代码

先看日志有没有出现`Compiling`或具体的`.java`文件错误。如果失败发生在下载依赖、解析Maven参数或启动插件之前，它属于环境/命令问题，不代表Java代码写错了。排错时先判断错误发生在哪一层。

### 程序可以启动，但聊天返回503

原因：没有设置`DEEPSEEK_API_KEY`。重新设置环境变量后必须重启Java进程。

### 返回401

原因通常是Key错误、Key失效或余额/账号权限问题。不要在日志或聊天中粘贴完整Key。

### 8080端口被占用

可以先查占用进程，或临时使用：

```powershell
.\mvnw.cmd spring-boot:run -Dspring-boot.run.arguments="--server.port=8081"
```

### 浏览器返回400

检查请求JSON是否包含`message`，以及内容是否为空或超过4000字符。

## 9. 你的练习

不要马上搜索完整答案，先自己完成：

1. 在`ChatRequest.message`上增加最小长度限制：至少输入2个字符。
2. 创建`GET /api/v1/hello`，返回字符串`Agent项目已启动`。
3. 使用浏览器或PowerShell验证两个功能。

完成后把代码或报错发给我，我会先检查你的思路，再进入流式输出。

提示：第1题不是再堆一个重复注解，而是思考怎样同时表达最小长度、最大长度和错误信息。

## 10. 本课面试题

1. Controller和Service为什么要分层？
2. 什么是构造器依赖注入？
3. `@RequestBody`解决什么问题？
4. 参数校验为什么不直接写在Controller的`if`中？
5. 普通模型调用为什么必须等待完整答案？
6. API Key为什么不能写进Git？

请尝试不用看文档回答一次完整数据流。
