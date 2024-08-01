package com.lantin.unitrade.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lantin.unitrade.domain.dto.OrderFormDTO;
import com.lantin.unitrade.domain.po.Order;


public interface IOrderService extends IService<Order> {

    Long createOrder(OrderFormDTO orderFormDTO, Long orderId);

    void markOrderPaySuccess(Long orderId);

    void cancelOrder(Long orderId);

    Long placeOrder(OrderFormDTO orderFormDTO);
}
