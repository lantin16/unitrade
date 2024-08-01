package com.lantin.unitrade.listener;

import com.lantin.unitrade.domain.dto.OrderFormDTO;
import com.lantin.unitrade.service.IOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import static com.lantin.unitrade.constant.MQConstants.*;
import static com.lantin.unitrade.constant.RedisConstants.*;

/**
 * 下单成功的消息监听类
 * 监听下单成功消息，完成数据库表创建订单记录并扣减数据库商品库存
 * @Author lantin
 * @Date 2024/8/1
 */

@Component
@RequiredArgsConstructor
public class createOrderListener {

    private final IOrderService orderService;

    /**
     * 监听下单成功消息，完成数据库表创建订单记录并扣减数据库商品库存
     * @param
     */
    @RabbitListener(bindings = @QueueBinding(   // 基于注解方式声明队列和交换机并完成绑定
            value = @Queue(name = ORDER_SUCCESS_QUEUE, durable = "true"),
            exchange = @Exchange(name = ORDER_DIRECT_EXCHANGE),    // 交换机默认类型就是DIRECT，默认是持久化的
            key = ORDER_SUCCESS_ROUTINGKEY  // routingKey
    ))
    public void listenCreateOrderSuccess(Message message, OrderFormDTO orderFormDTO) {
        // 1. 获取消息头中的订单id（用户id在消息转换器中就保存好了这里就不用再保存到ThreadLocal）
        Long orderId = message.getMessageProperties().getHeader("order-id");
        // 2. 调用createOrder方法创建订单并扣减库存
        orderService.createOrder(orderFormDTO, orderId);
    }
}
