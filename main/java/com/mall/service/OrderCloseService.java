package com.mall.service;

import com.mall.dao.OrderItemMapper;
import com.mall.dao.OrderMapper;
import com.mall.dao.ProductMapper;
import com.mall.pojo.Order;
import com.mall.pojo.OrderItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.*;

@Slf4j
@Service
public class OrderCloseService {

    @Resource private OrderMapper orderMapper;
    @Resource private OrderItemMapper orderItemMapper;
    @Resource private ProductMapper productMapper;

    /**
     * 超时到点：若订单仍为【未付款(10)】，则改为【已取消(0)】并按明细回补库存
     */
    @Transactional
    public void autoCancelAndRestore(Long orderNo) {
        Order order = orderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            log.warn("{} 订单不存在，跳过", orderNo);
            return;
        }
        // 仅处理仍未付款的订单
        if (!Integer.valueOf(10).equals(order.getStatus())) {
            log.info("{} 当前状态非未付款({})，无需取消", orderNo, order.getStatus());
            return;
        }

        // 1) 关单
        order.setStatus(0);               // 0 = 已取消
        order.setCloseTime(new Date());
        orderMapper.updateByPrimaryKeySelective(order);

        // 2) 回补库存：按订单明细逐条归还
        Set<Long> one = Collections.singleton(orderNo);
        List<OrderItem> items = orderItemMapper.selectByOrderNoSet(one);
        for (OrderItem it : items) {
            productMapper.addStock(it.getProductId(), it.getQuantity());
        }

        log.info("{} 超时未支付，已自动取消并完成库存回补", orderNo);
    }
}
