package com.lantin.unitrade.listener;

import com.lantin.unitrade.domain.po.Order;
import com.lantin.unitrade.enums.OrderStatus;
import com.lantin.unitrade.service.IOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import static com.lantin.unitrade.constant.MQConstants.*;

/**
 * 修改业务单状态的消息监听器
 * 支付成功后从消息队列中取出订单id并修改订单状态
 * @Author lantin
 * @Date 2024/8/1
 */

@Component
@RequiredArgsConstructor
public class PayStatusListener {

    private final IOrderService orderService;

    /**
     * 不能收到消息直接就标记订单已支付，为了保证业务幂等性，需要先进行业务判断
     * @param orderId
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = PAY_SUCCESS_QUEUE, durable = "true"),
            exchange = @Exchange(name = PAY_DIRECT_EXCHANGE),
            key = PAY_SUCCESS_ROUTINGKEY
    ))
    public void listenPaySuccess(Long orderId) {
        // 1. 查询订单
        Order order = orderService.getById(orderId);
        // 2. 判断订单状态，是否为未支付
        if (order == null || !OrderStatus.UNPAID.equalsValue(order.getStatus())) {
            // 订单状态并不是未支付，不做处理
            return;
        }
        // 3. 只在订单状态为未支付时才标记为已支付
        orderService.markOrderPaySuccess(orderId);
    }
}
