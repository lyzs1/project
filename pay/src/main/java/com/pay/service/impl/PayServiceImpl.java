package com.pay.service.impl;

import com.google.gson.Gson;
import com.pay.dao.PayInfoMapper;
import com.pay.enums.PayPlatformEnum;
import com.pay.pojo.PayInfo;
import com.pay.service.IPayService;
import com.lly835.bestpay.enums.BestPayPlatformEnum;
import com.lly835.bestpay.enums.BestPayTypeEnum;
import com.lly835.bestpay.enums.OrderStatusEnum;
import com.lly835.bestpay.model.PayRequest;
import com.lly835.bestpay.model.PayResponse;
import com.lly835.bestpay.service.BestPayService;
import lombok.extern.slf4j.Slf4j;
import com.pay.config.RabbitConfig;
import com.pay.config.ReliablePublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;


@Slf4j
@Service
public class PayServiceImpl implements IPayService {

	private final static String QUEUE_PAY_NOTIFY = "payNotify";

	@Autowired
	private BestPayService bestPayService;

	@Autowired
	private PayInfoMapper payInfoMapper;

	@Autowired
	private ReliablePublisher reliablePublisher;

	/**
	 * 创建/发起支付
	 *
	 * @param orderId
	 * @param amount
	 */
	@Override
	public PayResponse create(String orderId, BigDecimal amount, BestPayTypeEnum bestPayTypeEnum) {
		//落库一条支付单
		PayInfo payInfo = new PayInfo(Long.parseLong(orderId),
				PayPlatformEnum.getByBestPayTypeEnum(bestPayTypeEnum).getCode(),
				OrderStatusEnum.NOTPAY.name(),
				amount);
		payInfoMapper.insertSelective(payInfo);

		PayRequest request = new PayRequest();
		request.setOrderName("4559066-最好的支付sdk");
		request.setOrderId(orderId);
		request.setOrderAmount(amount.doubleValue());
		request.setPayTypeEnum(bestPayTypeEnum);

		//使用第三方SDK请求支付，并拿到response
		PayResponse response = bestPayService.pay(request);
		log.info("发起支付 response={}", response);

		return response;

	}

	/**
	 * 异步通知处理
	 * 微信/支付宝后台轮询请求支付系统的 notify_url（notify api）
	 * 商户系统对微信后台的每次请求执行asyncNotify
	 *
	 * @param notifyData
	 */
	@Override
	public String asyncNotify(String notifyData) {
		//1. 签名检验
		PayResponse payResponse = bestPayService.asyncNotify(notifyData);
		log.info("异步通知 response={}", payResponse);


		//2. 金额校验（从数据库查订单）
		//比较严重（正常情况下是不会发生的）发出告警：钉钉、短信
		PayInfo payInfo = payInfoMapper.selectByOrderNo(Long.parseLong(payResponse.getOrderId()));
		if (payInfo == null) {
			//告警
			throw new RuntimeException("通过orderNo查询到的结果是null");
		}
		//如果订单支付状态不是"已支付"
		if (!payInfo.getPlatformStatus().equals(OrderStatusEnum.SUCCESS.name())) {
			//Double类型比较大小，精度。1.00  1.0
			if (payInfo.getPayAmount().compareTo(BigDecimal.valueOf(payResponse.getOrderAmount())) != 0) {
				//告警
				throw new RuntimeException("异步通知中的金额和数据库里的不一致，orderNo=" + payResponse.getOrderId());
			}

			//3. 修改订单支付状态
			payInfo.setPlatformStatus(OrderStatusEnum.SUCCESS.name());
			payInfo.setPlatformNumber(payResponse.getOutTradeNo());
			payInfoMapper.updateByPrimaryKeySelective(payInfo);
		}

		//TODO pay发送MQ消息，mall系统接受MQ消息，目的：通知商城系统 更新对应订单状态
		//这里为了保证异步回调的幂等性，应该只有只有第一次把 platform_status 从非 SUCCESS 改到 SUCCESS，rows=1，
		// 它发 MQ；其余并发 rows=0，不发 MQ。（暂未实现）
		reliablePublisher.sendToQueue(RabbitConfig.QUEUE_PAY_NOTIFY, new Gson().toJson(payInfo));

		if (payResponse.getPayPlatformEnum() == BestPayPlatformEnum.WX) {
			// 由于微信/支付宝后台轮询请求notify_url，
			// 告诉微信/支付宝不要再轮询了
			// 必须向wx后台return 固定 XML 才会停止重试
			return "<xml>\n" +
					"  <return_code><![CDATA[SUCCESS]]></return_code>\n" +
					"  <return_msg><![CDATA[OK]]></return_msg>\n" +
					"</xml>";
		}else if (payResponse.getPayPlatformEnum() == BestPayPlatformEnum.ALIPAY) {
			//向支付宝return “success” 字符串即可
			return "success";
		}

		throw new RuntimeException("异步通知中错误的支付平台");
	}



	@Override
	public PayInfo queryByOrderId(String orderId) {
		return payInfoMapper.selectByOrderNo(Long.parseLong(orderId));
	}
}
