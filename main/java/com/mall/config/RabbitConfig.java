package com.mall.config;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String QUEUE_PAY_NOTIFY = "payNotify";

    /** 队列元数据持久化（durable） */
    @Bean
    public Queue payNotifyQueue() {
        return QueueBuilder.durable(QUEUE_PAY_NOTIFY).build();
    }

    /** 2.1.x：手动 ACK 容器工厂 */
    @Bean
    public SimpleRabbitListenerContainerFactory manualAckContainerFactory(ConnectionFactory cf) {
        SimpleRabbitListenerContainerFactory f = new SimpleRabbitListenerContainerFactory();
        f.setConnectionFactory(cf);
        f.setAcknowledgeMode(AcknowledgeMode.MANUAL); // 手动ACK
        f.setPrefetchCount(50);                        // 防止一次抓太多消息
        return f;
    }
}
