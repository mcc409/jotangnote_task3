package com.jotang.jotangnote.controller;

import com.jotang.jotangnote.entity.Note;
import com.jotang.jotangnote.mapper.NoteMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    @Value("${ai.api.url}")
    private String apiUrl;

    @Value("${ai.api.key}")
    private String apiKey;

    @Value("${ai.api.model}")
    private String model;

    @Autowired
    private NoteMapper noteMapper; // 注入数据库 Mapper

    private final RestTemplate restTemplate = new RestTemplate();
    private final Map<String, List<Map<String, Object>>> sessionMemory = new ConcurrentHashMap<>();

    @PostMapping
    public String chat(@RequestBody Map<String, String> request) {
        String userMessage = request.get("message");
        String sessionId = request.getOrDefault("sessionId", "default_user");

        // 1. 初始化历史记录
        List<Map<String, Object>> messages = sessionMemory.computeIfAbsent(sessionId, k -> {
            List<Map<String, Object>> newList = new ArrayList<>();
            Map<String, Object> sysMsg = new HashMap<>();
            sysMsg.put("role", "system");
            sysMsg.put("content", "你是 JotangNote 的智能助手，你可以调用工具查询笔记。");
            newList.add(sysMsg);
            return newList;
        });

        // 2. 把用户的输入加入历史
        Map<String, Object> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", userMessage);
        messages.add(userMsg);

        // 3. 定义 Tool（JSON Schema）
        Map<String, Object> tool = new HashMap<>();
        tool.put("type", "function");
        Map<String, Object> function = new HashMap<>();
        function.put("name", "get_note_by_id");
        function.put("description", "根据笔记 ID 查询笔记全文内容");
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

        // 4. 第一次请求 AI
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("tools", List.of(tool)); // 将工具给 AI

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(apiUrl, entity, Map.class);

        Map<String, Object> responseBody = response.getBody();
        List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");

        // 5. 检查 AI 是否想调用 Tool
        if (message.get("tool_calls") != null) {
            // AI 决定调用函数
            List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) message.get("tool_calls");
            Map<String, Object> firstCall = toolCalls.get(0);
            Map<String, Object> functionCall = (Map<String, Object>) firstCall.get("function");

            // 把 AI 决定调用工具的响应先存入历史
            messages.add(message);

            if ("get_note_by_id".equals(functionCall.get("name"))) {
                // 解析 AI 传给我们的参数 (JSON 字符串解析成 Map)
                String arguments = (String) functionCall.get("arguments");
                // 简单解析，为了不引入复杂 JSON 库，用正则提取数字
                Integer noteId = Integer.parseInt(arguments.replaceAll("[^0-9]", ""));

                // 真正去数据库查
                Note note = noteMapper.findById(noteId);
                String result = (note != null) ? note.getContent() : "没有找到该笔记";

                // 将工具执行结果打包成消息，存入历史
                Map<String, Object> toolMsg = new HashMap<>();
                toolMsg.put("role", "tool");
                toolMsg.put("tool_call_id", firstCall.get("id"));
                toolMsg.put("content", result);
                messages.add(toolMsg);

                // 6. 第二次请求 AI（把查询结果告诉它，让它总结）
                body.put("messages", messages);
                ResponseEntity<Map> response2 = restTemplate.postForEntity(apiUrl, new HttpEntity<>(body, headers), Map.class);
                List<Map<String, Object>> choices2 = (List<Map<String, Object>>) response2.getBody().get("choices");
                Map<String, Object> finalMessage = (Map<String, Object>) choices2.get(0).get("message");
                String finalReply = (String) finalMessage.get("content");

                // 存入历史
                messages.add(finalMessage);
                return finalReply;
            }
        }

        // 如果没调用工具，直接回复
        String reply = (String) message.get("content");
        messages.add(message);
        return reply;
    }
}