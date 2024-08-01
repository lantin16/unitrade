package com.lantin.unitrade.listener;

import com.lantin.unitrade.service.ICartService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Set;

import static com.lantin.unitrade.constant.MQConstants.*;

/**
 * 清空购物车的消息监听类
 * 监听订单创建成功消息，完成删除购物车本次购买的商品
 * @Author lantin
 * @Date 2024/8/1
 */
@Component
@RequiredArgsConstructor
public class clearCartListener {

    private final ICartService cartService;

    /**
     * 监听订单创建成功消息，完成删除购物车本次购买的商品
     * @param itemIds
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = CART_CLEAR_QUEUE, durable = "true"),
            exchange = @Exchange(name = ORDER_TOPIC_EXCHANGE, type = "topic"),
            key = CART_CLEAR_ROUTINGKEY
    ))
    public void listenClearCart(Set<Long> itemIds) {
        cartService.removeByItemIds(itemIds);
    }
}
