package com.pay.controller;

import com.pay.pojo.PayInfo;
import com.pay.service.impl.PayServiceImpl;
import com.lly835.bestpay.config.WxPayConfig;
import com.lly835.bestpay.enums.BestPayTypeEnum;
import com.lly835.bestpay.model.PayResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;


@Controller
@RequestMapping("/pay")
@Slf4j
public class PayController {

	@Autowired
	private PayServiceImpl payService;

	@Autowired
	private WxPayConfig wxPayConfig;


	//微信支付流程：
	// 使用Native支付
	// 模式二 ：一次性qrcode， 绑定订单
	//
	//浏览器跳转到此url(前端负责跳转)
	// ——> 调用统一下单API(包含在best-pay-sdk)  返回code_url(用于生成支付二维码的url,含完整支付参数)
	// ——> 支付链接转为qrcode(前端实现)
	// ——> 用户扫码跳转支付
	// ——> 微信服务器向 notify url发送请求
	// notify url：异步接受微信支付结果通知的回调地址，必须外网可访问，不可携带参数
	// returnUrl: 提供一个返回链接，让用户可以从支付页面返回到商户网站
	// 商户系统对支付结果通知一定要做签名验证，并校验两侧订单金额是否一致，防止假通知
	@GetMapping("/create")
	public ModelAndView create(@RequestParam("orderId") String orderId,
							   @RequestParam("amount") BigDecimal amount,
							   @RequestParam("payType") BestPayTypeEnum bestPayTypeEnum
							   ) {
		PayResponse response = payService.create(orderId, amount, bestPayTypeEnum);

		//支付方式不同，渲染就不同, WXPAY_NATIVE使用codeUrl,  ALIPAY_PC使用body
		Map<String, String> map = new HashMap<>();
		//微信Native支付，前端据此生成二维码
		if (bestPayTypeEnum == BestPayTypeEnum.WXPAY_NATIVE) {
			map.put("codeUrl", response.getCodeUrl());
			map.put("orderId", orderId);
			map.put("returnUrl", wxPayConfig.getReturnUrl());
			return new ModelAndView("createForWxNative", map);
		}else if (bestPayTypeEnum == BestPayTypeEnum.ALIPAY_PC) {
			map.put("body", response.getBody());
			return new ModelAndView("createForAlipayPc", map);
		}

		throw new RuntimeException("暂不支持的支付类型");
	}

	@PostMapping("/notify")
	@ResponseBody
	public String asyncNotify(@RequestBody String notifyData) {
		return payService.asyncNotify(notifyData);
	}

	// 前端何时跳转到returnUrl
	// 前端定时轮询此接口，直到platformStatus === 'SUCCESS'
	// 则前端跳转至returnUrl
	@GetMapping("/queryByOrderId")
	@ResponseBody
	public PayInfo queryByOrderId(@RequestParam String orderId) {
		log.info("查询支付记录...");
		return payService.queryByOrderId(orderId);
	}
}
/**
 * 微信支付统一下单流程总结
 *
 * 准备阶段：
 * 配置微信支付所需的参数（appId, mchId, mchKey等）
 * 设置异步通知URL（notifyUrl）和同步返回URL（returnUrl）
 *
 * 发起支付：
 * 构建支付请求参数（订单ID、金额、支付类型等）
 * 通过SDK调用微信支付统一下单API
 * 获取返回的codeUrl
 *
 * 前端展示：
 * 将codeUrl传递给前端
 * 前端使用codeUrl生成二维码
 * 显示订单信息和返回链接（returnUrl）
 *
 * 用户支付：
 * 用户扫描二维码
 * 在微信APP中完成支付
 * 支付完成后可以点击返回链接回到商户网站
 *
 * 支付结果处理：
 * 微信服务器通过notifyUrl异步通知支付结果
 * 系统验证支付结果并更新订单状态
 * 通过消息队列通知其他系统支付完成
 */

/**
 * 支付宝支付统一下单流程总结
 *
 * 准备阶段：
 * 配置支付宝支付所需的参数（appId, privateKey, publicKey等）
 * 设置异步通知URL（notifyUrl）和同步返回URL（returnUrl）
 *
 * 发起支付：
 * 构建支付请求参数（订单ID、金额、支付类型等）
 * 通过SDK调用支付宝支付接口
 * 获取返回的HTML表单内容（body）
 *
 * 前端展示：
 * 将body传递给前端
 * 前端直接渲染HTML表单内容
 * 表单会自动提交，跳转到支付宝支付页面
 *
 * 用户支付：
 * 用户在支付宝页面完成支付
 * 支付完成后支付宝会跳转到returnUrl指定的页面
 * 用户回到商户网站
 *
 * 支付结果处理：
 * 支付宝服务器通过notifyUrl异步通知支付结果
 * 系统验证支付结果并更新订单状态
 * 通过消息队列通知其他系统支付完成
 */

