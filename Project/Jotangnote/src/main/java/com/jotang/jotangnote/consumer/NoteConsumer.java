package com.jotang.jotangnote.consumer;

import com.jotang.jotangnote.config.RabbitConfig;
import com.jotang.jotangnote.entity.NoteMessage;
import com.jotang.jotangnote.mapper.NoteMapper;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class NoteConsumer {

    @Autowired
    private NoteMapper noteMapper;

    @RabbitListener(queues = RabbitConfig.NOTE_QUEUE)
    public void handle(NoteMessage message) {
        System.out.println("【MQ 消费者】收到消息：" + message.getOperation());

        switch (message.getOperation()) {
            case "CREATE":
                noteMapper.insert(message.getNote());
                break;
            case "UPDATE":
                noteMapper.update(message.getNote());
                break;
            case "DELETE":
                noteMapper.deleteById(message.getNote().getId());
                break;
        }
    }
}