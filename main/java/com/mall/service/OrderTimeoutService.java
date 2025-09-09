package com.mall.service;

import com.mall.pojo.Order;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class OrderTimeoutService {

    @Resource private OrderCloseService orderCloseService;

    // 复用一个调度线程池，避免反复 new
    private final ScheduledThreadPoolExecutor scheduler =
            new ScheduledThreadPoolExecutor(3, r -> {
                Thread t = new Thread(r);
                t.setName("order-timeout-" + t.getId());
                t.setDaemon(true);
                return t;
            });

    /**
     * 创建订单成功后调用：“48小时后尝试取消并回补库存”
     */
    public void scheduleCancel(Order order) {
        Long orderNo = order.getOrderNo();
        log.info("{} 订单开始存储定时任务", orderNo);

        scheduler.schedule(() -> {
            log.info("{} 订单开始触发定时任务", orderNo);
            orderCloseService.autoCancelAndRestore(orderNo);
        }, 2, TimeUnit.DAYS);
    }
}
