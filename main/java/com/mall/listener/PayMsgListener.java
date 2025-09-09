package com.mall.listener;

import com.google.gson.Gson;
import com.mall.service.IOrderService;
// 选择你项目中可解析的 PayInfo 类（若在 pay 包里有同名类，可复制一个 Mall 侧 DTO 或复用已有）
import com.mall.pojo.PayInfo;  // 若没有此类，请在 mall 侧建一个字段一致的 DTO
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

import static com.mall.config.RabbitConfig.QUEUE_PAY_NOTIFY;

@Component
@Slf4j
public class PayMsgListener {

	@Autowired
	private IOrderService orderService;

	@RabbitListener(queues = QUEUE_PAY_NOTIFY, containerFactory = "manualAckContainerFactory")
	public void process(Message message, Channel channel) throws Exception {
		long tag = message.getMessageProperties().getDeliveryTag();
		String body = new String(message.getBody(), StandardCharsets.UTF_8);

		try {
			log.info("[payNotify] 收到消息：{}", body);
			PayInfo payInfo = new Gson().fromJson(body, PayInfo.class);

			if ("SUCCESS".equals(payInfo.getPlatformStatus())) {
				// paid() 自身幂等（你已实现），多次调用也安全
				orderService.paid(payInfo.getOrderNo());
			}

			// 业务处理完成后再 ACK，避免“未处理就 ack”导致丢消息
			channel.basicAck(tag, false);

		} catch (Exception e) {
			log.error("[payNotify] 处理失败，NACK 并重回队列", e);
			// 回队重投；由于 paid() 幂等，不怕重复
			channel.basicNack(tag, false, true);
		}
	}
}
