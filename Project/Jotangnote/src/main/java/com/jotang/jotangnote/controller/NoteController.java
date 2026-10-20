package com.jotang.jotangnote.controller;

import org.springframework.data.redis.core.RedisTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.jotang.jotangnote.entity.Note;
import com.jotang.jotangnote.mapper.NoteMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import com.jotang.jotangnote.config.RabbitConfig;
import com.jotang.jotangnote.entity.NoteMessage;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@RestController
@RequestMapping("/api/notes")
public class NoteController {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private NoteMapper noteMapper;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    // 新增笔记：从请求头拿 userId
    @PostMapping
    public Note create(@RequestBody Note note,
                       @RequestHeader(value = "userId", required = false) Integer userId) {
        note.setUserId(userId);
        note.setCreatedAt(LocalDateTime.now());
        // 不再直接存数据库，改成发消息到 MQ
        rabbitTemplate.convertAndSend(RabbitConfig.NOTE_QUEUE, new NoteMessage("CREATE", note));
        return note;
    }

    // 查询笔记
    @GetMapping("/{id}")
    public Note get(@PathVariable Integer id) throws Exception {
        String key = "note:" + id;

        // 1. 先查 Redis
        String cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            System.out.println("【缓存命中】从 Redis 返回");
            return objectMapper.readValue(cached, Note.class);
        }

        // 2. Redis 没有，查 MySQL
        System.out.println("【缓存未命中】从 MySQL 查询");
        Note note = noteMapper.findById(id);
        if (note == null) {
            return null;
        }

        // 3. 写入 Redis
        redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(note));
        return note;
    }

    // 修改笔记：只能改自己的
    @PutMapping("/{id}")
    public String update(@PathVariable Integer id, @RequestBody Note note,
                         @RequestHeader(value = "userId", required = false) Integer userId) {
        Note existing = noteMapper.findById(id);
        if (existing == null) {
            return "笔记不存在";
        }
        if (userId == null || !userId.equals(existing.getUserId())) {
            return "无权修改别人的笔记";
        }
        note.setId(id);
        // 改成发消息到 MQ
        rabbitTemplate.convertAndSend(RabbitConfig.NOTE_QUEUE, new NoteMessage("UPDATE", note));
        redisTemplate.delete("note:" + id);
        return "修改成功";
    }

    // 删除笔记：只能删自己的
    @DeleteMapping("/{id}")
    public String delete(@PathVariable Integer id,
                         @RequestHeader(value = "userId", required = false) Integer userId) {
        Note existing = noteMapper.findById(id);
        if (existing == null) {
            return "笔记不存在";
        }
        if (userId == null || !userId.equals(existing.getUserId())) {
            return "无权删除别人的笔记";
        }
        Note note = new Note();
        note.setId(id);
        // 改成发消息到 MQ
        rabbitTemplate.convertAndSend(RabbitConfig.NOTE_QUEUE, new NoteMessage("DELETE", note));
        redisTemplate.delete("note:" + id);
        return "deleted";
    }

    // 查询所有
    @GetMapping
    public List<Note> findAll() {
        return noteMapper.findAll();
    }
}