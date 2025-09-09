package com.pay.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

@Slf4j
@Component
public class ReliablePublisher {

    private final RabbitTemplate rabbitTemplate;

    private static final Map<String, Pending> PENDING = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "publisher-retry");
                t.setDaemon(true);
                return t;
            });

    @Data
    private static class Pending {
        final String id;
        final String routingKey;   // 默认交换机下=队列名
        final String payload;      // JSON
        volatile int attempts = 1; // 已发送次数
        volatile long lastSendAt = Instant.now().toEpochMilli();
    }

    public ReliablePublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;

        // pay: ReliablePublisher 构造器里绑定 confirm回调
        this.rabbitTemplate.setConfirmCallback(this::onConfirm);

        // Return 回调
        this.rabbitTemplate.setReturnCallback(this::onReturned);

        // 轮询超时未确认的消息，兜底重发
        SCHEDULER.scheduleAtFixedRate(this::retryTimeouts, 10, 10, TimeUnit.SECONDS);
    }

    /** 业务方调用：发送到默认交换机，routingKey=队列名 */
    public void sendToQueue(String queue, String payload) {
        String id = queue + ":" + UUID.randomUUID().toString();
        Pending p = new Pending(id, queue, payload);
        PENDING.put(id, p);

        rabbitTemplate.convertAndSend(
                "",                 // default exchange
                queue,              // routing key
                payload,
                msg -> {
                    //deliveryMode = 2 保证消息持久化到磁盘
                    msg.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT); // deliveryMode=2
                    msg.getMessageProperties().setMessageId(id);
                    return msg;
                },
                new CorrelationData(id)
        );
        log.info("[MQ-PUB] sent id={}, queue={}, len={}", id, queue, payload.length());
    }

    // Confirm 回调：只有 broker 写盘成功才 ack=true；否则重发一次
    private void onConfirm(CorrelationData cd, boolean ack, String cause) {
        if (cd == null) return;
        Pending p = PENDING.get(cd.getId());
        if (ack) {
            PENDING.remove(cd.getId());
            log.info("[MQ-CONFIRM] ack=true id={}", cd.getId());
        } else if (p != null) {
            log.warn("[MQ-CONFIRM] ack=false id={}, cause={}", cd.getId(), cause);
            resend(p);
        }
    }

    // Return 回调：路由失败（交换机/路由键问题）也能被捕获并重发
    private void onReturned(Message message, int replyCode, String replyText, String exchange, String routingKey) {
        String id = message.getMessageProperties().getMessageId();
        log.error("[MQ-RETURN] id={}, code={}, text={}, ex={}, rk={}", id, replyCode, replyText, exchange, routingKey);
        Pending p = PENDING.get(id);
        if (p != null) {
            resend(p);
        }
    }

    /** >10s 未确认且 attempts<3 则重发 */
    private void retryTimeouts() {
        long now = Instant.now().toEpochMilli();
        for (Pending p : PENDING.values()) {
            if (now - p.lastSendAt > 10_000 && p.attempts < 3) {
                log.warn("[MQ-RETRY] id={} timeout, attempts={}", p.id, p.attempts);
                resend(p);
            }
        }
    }

    private void resend(Pending p) {
        p.attempts++;
        p.lastSendAt = Instant.now().toEpochMilli();
        rabbitTemplate.convertAndSend(
                "",
                p.routingKey,
                p.payload.getBytes(StandardCharsets.UTF_8),
                msg -> {
                    msg.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    msg.getMessageProperties().setMessageId(p.id);
                    return msg;
                },
                new CorrelationData(p.id)
        );
        log.info("[MQ-RESEND] id={}, attempts={}", p.id, p.attempts);
    }
}
