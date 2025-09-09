package com.mall.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.mall.dao.OrderItemMapper;
import com.mall.dao.OrderMapper;
import com.mall.dao.ProductMapper;
import com.mall.dao.ShippingMapper;
import com.mall.enums.OrderStatusEnum;
import com.mall.enums.PaymentTypeEnum;
import com.mall.enums.ProductStatusEnum;
import com.mall.enums.ResponseEnum;
import com.mall.pojo.*;
import com.mall.service.ICartService;
import com.mall.service.IOrderService;
import com.mall.service.OrderTimeoutService;
import com.mall.vo.OrderItemVo;
import com.mall.vo.OrderVo;
import com.mall.vo.ResponseVo;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;


@Service
public class OrderServiceImpl implements IOrderService {

	@Autowired
	private ShippingMapper shippingMapper;

	@Autowired
	private ICartService cartService;

	@Autowired
	private ProductMapper productMapper;

	@Autowired
	private OrderMapper orderMapper;

	@Autowired
	private OrderItemMapper orderItemMapper;

	@Autowired
	private OrderTimeoutService orderTimeoutService;

	//在前端页面进入下单页时就生成唯一的orderNo，并在请求create接口时传入
	//配合mysql对order_no建立唯一索引。可以保证下单的幂等性
	//防止超时抖动、网络重传
	@Override
	@Transactional
	public ResponseVo<OrderVo> create(Integer uid, Integer shippingId) {
		//收货地址校验（总之要查出来的）
		Shipping shipping = shippingMapper.selectByUidAndShippingId(uid, shippingId);
		if (shipping == null) {
			return ResponseVo.error(ResponseEnum.SHIPPING_NOT_EXIST);
		}

		//获取购物车，校验（是否有商品、库存）
		List<Cart> cartList = cartService.listForCart(uid).stream()
				.filter(Cart::getProductSelected)
				.collect(Collectors.toList());
		if (CollectionUtils.isEmpty(cartList)) {
			return ResponseVo.error(ResponseEnum.CART_SELECTED_IS_EMPTY);
		}

		//不使用for循环内查询mysql。耗时严重
		//获取cartList里的productIds
		//获取productIds集合，通过selectByProductIdSet一次性查询
		Set<Integer> productIdSet = cartList.stream()
				.map(Cart::getProductId)
				.collect(Collectors.toSet());
		//这里为了防止超卖，需要在查询product的sql语句后加FOR UPDATE
		//从而自动加上行级排他锁
		List<Product> productList = productMapper.selectByProductIdSet(productIdSet);
		Map<Integer, Product> map  = productList.stream()
				.collect(Collectors.toMap(Product::getId, product -> product));

		List<OrderItem> orderItemList = new ArrayList<>();
		Long orderNo = generateOrderNo();
		//对每个cart对象 与 对应 product对象 逐个校验
		for (Cart cart : cartList) {

			Product product = map.get(cart.getProductId());
			//是否有商品
			if (product == null) {
				return ResponseVo.error(ResponseEnum.PRODUCT_NOT_EXIST,
						"商品不存在. productId = " + cart.getProductId());
			}
			//商品上下架状态
			if (!ProductStatusEnum.ON_SALE.getCode().equals(product.getStatus())) {
				return ResponseVo.error(ResponseEnum.PRODUCT_OFF_SALE_OR_DELETE,
						"商品不是在售状态. " + product.getName());
			}

			//库存是否充足
			if (product.getStock() < cart.getQuantity()) {
				return ResponseVo.error(ResponseEnum.PROODUCT_STOCK_ERROR,
						"库存不正确. " + product.getName());
			}

			OrderItem orderItem = buildOrderItem(uid, orderNo, cart.getQuantity(), product);
			orderItemList.add(orderItem);

			//减库存， update语句隐示加排他锁
			product.setStock(product.getStock() - cart.getQuantity());
			int row = productMapper.updateByPrimaryKeySelective(product);
			if (row <= 0) {
				return ResponseVo.error(ResponseEnum.ERROR);
			}
		}

		//计算总价，只计算选中的商品
		//生成订单，入库：order（订单主表）和order_item（订单明细表），事务
		//默认status是10（未付款状态）
		Order order = buildOrder(uid, orderNo, shippingId, orderItemList);

		int rowForOrder = orderMapper.insertSelective(order);
		if (rowForOrder <= 0) {
			return ResponseVo.error(ResponseEnum.ERROR);
		}

		int rowForOrderItem = orderItemMapper.batchInsert(orderItemList);
		if (rowForOrderItem <= 0) {
			return ResponseVo.error(ResponseEnum.ERROR);
		}

		//更新购物车（删除订单中的商品）
		//Redis有事务(打包命令)，不能回滚
		for (Cart cart : cartList) {
			cartService.delete(uid, cart.getProductId());
		}

		//若48h后未支付成功，超时自动取消订单 + 回补库存
		orderTimeoutService.scheduleCancel(order);

		//构造orderVo
		OrderVo orderVo = buildOrderVo(order, orderItemList, shipping);
		return ResponseVo.success(orderVo);
	}

	@Override
	public ResponseVo<PageInfo> list(Integer uid, Integer pageNum, Integer pageSize) {
		PageHelper.startPage(pageNum, pageSize);
		List<Order> orderList = orderMapper.selectByUid(uid);

		Set<Long> orderNoSet = orderList.stream()
				.map(Order::getOrderNo)
				.collect(Collectors.toSet());
		List<OrderItem> orderItemList = orderItemMapper.selectByOrderNoSet(orderNoSet);
		Map<Long, List<OrderItem>> orderItemMap = orderItemList.stream()
				.collect(Collectors.groupingBy(OrderItem::getOrderNo));

		Set<Integer> shippingIdSet = orderList.stream()
				.map(Order::getShippingId)
				.collect(Collectors.toSet());
		List<Shipping> shippingList = shippingMapper.selectByIdSet(shippingIdSet);
		Map<Integer, Shipping> shippingMap = shippingList.stream()
				.collect(Collectors.toMap(Shipping::getId, shipping -> shipping));

		List<OrderVo> orderVoList = new ArrayList<>();
		for (Order order : orderList) {
			OrderVo orderVo = buildOrderVo(order,
					orderItemMap.get(order.getOrderNo()),
					shippingMap.get(order.getShippingId()));
			orderVoList.add(orderVo);
		}
		PageInfo pageInfo = new PageInfo<>(orderList);
		pageInfo.setList(orderVoList);

		return ResponseVo.success(pageInfo);
	}

	@Override
	public ResponseVo<OrderVo> detail(Integer uid, Long orderNo) {
		Order order = orderMapper.selectByOrderNo(orderNo);
		if (order == null || !order.getUserId().equals(uid)) {
			return ResponseVo.error(ResponseEnum.ORDER_NOT_EXIST);
		}
		Set<Long> orderNoSet = new HashSet<>();
		orderNoSet.add(order.getOrderNo());
		List<OrderItem> orderItemList = orderItemMapper.selectByOrderNoSet(orderNoSet);

		Shipping shipping = shippingMapper.selectByPrimaryKey(order.getShippingId());

		OrderVo orderVo = buildOrderVo(order, orderItemList, shipping);
		return ResponseVo.success(orderVo);
	}

	@Override
	@Transactional
	public ResponseVo cancel(Integer uid, Long orderNo) {
		Order order = orderMapper.selectByOrderNo(orderNo);
		if (order == null || !order.getUserId().equals(uid)) {
			return ResponseVo.error(ResponseEnum.ORDER_NOT_EXIST);
		}
		//只有[未付款]订单可以取消，看自己公司业务
		if (!order.getStatus().equals(OrderStatusEnum.NO_PAY.getCode())) {
			return ResponseVo.error(ResponseEnum.ORDER_STATUS_ERROR);
		}

		order.setStatus(OrderStatusEnum.CANCELED.getCode());
		order.setCloseTime(new Date());
		int row = orderMapper.updateByPrimaryKeySelective(order);
		if (row <= 0) {
			return ResponseVo.error(ResponseEnum.ERROR);
		}
		// —— 回补库存：根据订单明细逐条恢复 —— //
		Set<Long> one = Collections.singleton(orderNo);
		List<OrderItem> items = orderItemMapper.selectByOrderNoSet(one);
		for (OrderItem it : items) {
			productMapper.addStock(it.getProductId(), it.getQuantity());
		}
		return ResponseVo.success();
	}

	/**
	 * MQ 监听到支付成功后调用
	 * 目标：解决“超时自动取消 vs 支付晚到”的并发，按你描述使用乐观锁：
	 * - 开始时记录订单的 update_time（版本）
	 * - 若看到已取消，只有在 status 仍为已取消且 update_time 仍等于刚读到的版本，才改成已支付
	 */
	@Transactional
	@Override
	public void paid(Long orderNo) {
		// 1) 读快照，拿到当时的 update_time（版本）
		Order snap = orderMapper.selectByOrderNo(orderNo);
		if (snap == null) {
			throw new RuntimeException(ResponseEnum.ORDER_NOT_EXIST.getDesc() + "订单id:" + orderNo);
		}
		Date snapVersion = snap.getUpdateTime();
		Date now = new Date();

		// 2) 尝试在“快照版本”上把 NO_PAY(10) -> PAID(20)
		int n = orderMapper.updateToPaidIfNoPayWithVersion(orderNo, snapVersion, now);
		if (n == 1) {
			// 正常路径：在我们读到的那一版上把未付款置为已付款
			return;
		}

		// 3) 失败：说明并发发生（可能被超时关单改成已取消），重读当前真实状态
		Order cur = orderMapper.selectByOrderNo(orderNo);
		if (cur == null) {
			// 极端情况（被删等），这里按需处理：直接返回或记录日志
			throw new RuntimeException(ResponseEnum.ORDER_NOT_EXIST.getDesc() + "订单id:" + orderNo);
		}

		// 3.1 幂等：若已是已付款或更后状态，直接返回成功（避免重复消费风暴）
		if (Objects.equals(cur.getStatus(), OrderStatusEnum.PAID.getCode())
				|| Objects.equals(cur.getStatus(), OrderStatusEnum.SHIPPED.getCode())
				|| Objects.equals(cur.getStatus(), OrderStatusEnum.TRADE_SUCCESS.getCode())) {
			return;
		}

		// 3.2 若当前是“已取消(0)”，按你的描述用乐观锁“复活”为已支付：
		// “只有当状态仍是已取消且 update_time 还等于我刚读到的版本，我才把它改成已支付”
		if (Objects.equals(cur.getStatus(), OrderStatusEnum.CANCELED.getCode())) {
			int m = orderMapper.reviveCanceledToPaidWithVersion(orderNo, cur.getUpdateTime(), now);
			if (m == 1) {
				// 复活成功
				return;
			}
			// 版本不匹配 => 并发又动了，避免脏写；这里给出明确错误（也可仅记录日志）
			throw new RuntimeException("订单状态已被并发修改，复活为已支付失败，订单id:" + orderNo);
		}

		// 3.3 其它状态：仍按原有行为报错
		throw new RuntimeException(ResponseEnum.ORDER_STATUS_ERROR.getDesc() + "订单id:" + orderNo);
	}

	private OrderVo buildOrderVo(Order order, List<OrderItem> orderItemList, Shipping shipping) {
		OrderVo orderVo = new OrderVo();
		BeanUtils.copyProperties(order, orderVo);

		List<OrderItemVo> OrderItemVoList = orderItemList.stream().map(e -> {
			OrderItemVo orderItemVo = new OrderItemVo();
			BeanUtils.copyProperties(e, orderItemVo);
			return orderItemVo;
		}).collect(Collectors.toList());
		orderVo.setOrderItemVoList(OrderItemVoList);

		if (shipping != null) {
			orderVo.setShippingId(shipping.getId());
			orderVo.setShippingVo(shipping);
		}

		return orderVo;
	}

	private Order buildOrder(Integer uid,
							 Long orderNo,
							 Integer shippingId,
							 List<OrderItem> orderItemList
							 ) {
		BigDecimal payment = orderItemList.stream()
				.map(OrderItem::getTotalPrice)
				.reduce(BigDecimal.ZERO, BigDecimal::add);
		//等价于
		//BigDecimal payment = BigDecimal.ZERO;
		//for (OrderItem item : orderItemList) {
		//    payment = payment.add(item.getTotalPrice());
		//}
		Order order = new Order();
		order.setOrderNo(orderNo);
		order.setUserId(uid);
		order.setShippingId(shippingId);
		order.setPayment(payment);
		order.setPaymentType(PaymentTypeEnum.PAY_ONLINE.getCode());
		order.setPostage(0);
		order.setStatus(OrderStatusEnum.NO_PAY.getCode());
		return order;
	}

	/**
	 * 企业级：分布式唯一id/主键
	 * @return
	 */
	private Long generateOrderNo() {
		return System.currentTimeMillis() + new Random().nextInt(999);
	}

	private OrderItem buildOrderItem(Integer uid, Long orderNo, Integer quantity, Product product) {
		OrderItem item = new OrderItem();
		item.setUserId(uid);
		item.setOrderNo(orderNo);
		item.setProductId(product.getId());
		item.setProductName(product.getName());
		item.setProductImage(product.getMainImage());
		item.setCurrentUnitPrice(product.getPrice());
		item.setQuantity(quantity);
		item.setTotalPrice(product.getPrice().multiply(BigDecimal.valueOf(quantity)));
		return item;
	}
}
