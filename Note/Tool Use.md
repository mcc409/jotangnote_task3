## Part 3：实现 Tool Use

### 1. 功能说明

在普通的对话中，AI 只能基于数据库来回答，它无法直接读取 JotangNote 数据库里的最新笔记。
通过 **Tool Use** ，可以赋予 AI 操作我们本地系统的能力。

### 2. 核心过程

Tool Use 的底层流程如下：

1. **告诉 AI 有什么工具**：我们在请求中加入 `tools`，告诉 AI：“如果你需要查笔记，可以调用 `get_note_by_id`，参数是 `id`”。
2. **请求 AI**：用户问：“帮我查一下 id 为 1 的笔记”。
3. **AI 决定调用工具**：AI 发现需要用到工具，于是不直接回复文本，而是返回一个 `tool_calls` 字段，里面包含函数名 `get_note_by_id` 和参数 `id=1`。
4. **后端拦截执行**：我们的 Java 代码收到 `tool_calls` 后，去 MySQL 中查询 `id=1` 的笔记内容。
5. **将结果再次发给 AI**：后端把查到的笔记内容作为 `role: tool` 的消息，连同之前的上下文再次发给 AI。
6. **AI 生成最终回复**：AI 拿到数据后，总结返回给用户。

### 3. 核心代码

以下代码位于 `ChatController.java` 中：

- **定义工具 Schema（告知 AI 有什么工具）**
```java
// 构造 Tool 定义
Map<String, Object> tool = new HashMap<>();
tool.put("type", "function");
Map<String, Object> function = new HashMap<>();
function.put("name", "get_note_by_id");
function.put("description", "根据笔记 ID 查询笔记全文内容");

// 定义参数：需要传入一个整数类型的 id
Map<String, Object> parameters = new HashMap<>();
parameters.put("type", "object");
Map<String, Object> properties = new HashMap<>();
Map<String, Object> idProp = new HashMap<>();
idProp.put("type", "integer");
idProp.put("description", "笔记的唯一 ID");
properties.put("id", idProp);
parameters.put("properties", properties);
parameters.put("required", List.of("id"));

function.put("parameters", parameters);
tool.put("function", function);

// 将工具加入请求体
body.put("tools", List.of(tool)); 
```

- 解析 AI 的调用请求（执行本地函数）

```java
// 第一次请求 AI 后，检查 AI 是否打算调用工具
if (message.get("tool_calls") != null) {
    List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) message.get("tool_calls");
    Map<String, Object> firstCall = toolCalls.get(0);
    Map<String, Object> functionCall = (Map<String, Object>) firstCall.get("function");
    
    // 1. 先把 AI 决定调用工具的响应加入历史记忆
    messages.add(message);

    if ("get_note_by_id".equals(functionCall.get("name"))) {
        // 2. 解析 AI 传过来的参数
        String arguments = (String) functionCall.get("arguments");
        // 简单解析 JSON 字符串提取数字 ID
        Integer noteId = Integer.parseInt(arguments.replaceAll("[^0-9]", ""));

        // 3. 真正去 MySQL 查数据库
        Note note = noteMapper.findById(noteId);
        String result = (note != null) ? note.getContent() : "没有找到该笔记";

        // 4. 把查询结果封装成 role: "tool" 的消息，存入历史
        Map<String, Object> toolMsg = new HashMap<>();
        toolMsg.put("role", "tool");
        toolMsg.put("tool_call_id", firstCall.get("id")); // 必须对应 AI 传来的 call_id
        toolMsg.put("content", result);
        messages.add(toolMsg);

        // 5. 携带查询结果，第二次请求 AI（让 AI 组织语言）
        body.put("messages", messages);
        ResponseEntity<Map> response2 = restTemplate.postForEntity(apiUrl, new HttpEntity<>(body, headers), Map.class);
        
        // 6. 获取最终给用户看的文本回复
        List<Map<String, Object>> choices2 = (List<Map<String, Object>>) response2.getBody().get("choices");
        Map<String, Object> finalMessage = (Map<String, Object>) choices2.get(0).get("message");
        String finalReply = (String) finalMessage.get("content");

        messages.add(finalMessage); // 计入上下文
        return finalReply;
    }
}
```

### 4. 测试过程与结果

测试输入：
在接口测试工具中发送：

"帮我查一下 id 为 1 的笔记内容，并告诉我里面写了什么。"

测试现象：
用户发送后，AI 会“思考”几秒钟（后台经历了两轮 HTTP 请求，并去 MySQL 查了一次），然后返回结果。

测试截图：
./imgs/chat-tool-use.png

AI 返回内容：

“ID 为 1 的笔记内容如下：

这是我的第一篇笔记”