package com.mall.dao;

import com.mall.pojo.Order;
import io.lettuce.core.dynamic.annotation.Param;

import java.util.Date;
import java.util.List;

public interface OrderMapper {
    int deleteByPrimaryKey(Integer id);

    int insert(Order record);

    int insertSelective(Order record);

    Order selectByPrimaryKey(Integer id);

    int updateByPrimaryKeySelective(Order record);

    int updateByPrimaryKey(Order record);

    List<Order> selectByUid(Integer uid);

    Order selectByOrderNo(Long orderNo);


    /** 在指定版本上把 NO_PAY(10) -> PAID(20)（快照 CAS） */
    int updateToPaidIfNoPayWithVersion(@Param("orderNo") Long orderNo,
                                       @Param("lastUpdateTime") Date lastUpdateTime,
                                       @Param("paymentTime") Date paymentTime);

    /** 在指定版本上把 CANCELED(0) -> PAID(20)（复活，仍是取消且版本匹配才成功） */
    int reviveCanceledToPaidWithVersion(@Param("orderNo") Long orderNo,
                                        @Param("lastUpdateTime") Date lastUpdateTime,
                                        @Param("paymentTime") Date paymentTime);
}