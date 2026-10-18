# 接入大模型

项目使用了 **DeepSeek** 的 API。配置写在 `application.properties` 中：
```properties
ai.api.url=https://api.deepseek.com/chat/completions
ai.api.key=sk-xxxxxxxxxxxxxxxx
ai.api.model=deepseek-chat
```

在 ChatController 中，使用 Spring 发送 HTTP POST 请求，把用户的输入包装成 JSON 发给大模型，并解析返回的 JSON 提取回复。

# 会话记忆管理

大模型本身是无状态的，在 ChatController 中使用一个 ConcurrentHashMap 来保存不同用户的聊天记录：

```java
// Key 是 sessionId，Value 是历史消息列表
private final Map<String, List<Map<String, Object>>> sessionMemory = new ConcurrentHashMap<>();
```

每次请求时，我们会把用户的最新消息追加到这个 List 中，然后将整个 List 作为文本 发给 AI。AI 回复后，再把 AI 的回复也追加进去。这样就实现了多轮对话的“记忆”。