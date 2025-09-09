package com.pay.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String QUEUE_PAY_NOTIFY = "payNotify";

    /** durable 队列（仅持久化队列元数据） */
    @Bean
    public Queue payNotifyQueue() {
        return QueueBuilder.durable(QUEUE_PAY_NOTIFY).build();
    }

    /** 2.1.x：模板只需要 mandatory=true；Confirm/Return 回调放到 ReliablePublisher 里设置，避免循环依赖 */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory cf) {
        RabbitTemplate tpl = new RabbitTemplate(cf);
        tpl.setMandatory(true); // 才会触发 ReturnCallback
        return tpl;
    }
}
