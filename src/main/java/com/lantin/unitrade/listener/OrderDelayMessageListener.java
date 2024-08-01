package com.lantin.unitrade.listener;


import com.lantin.unitrade.constant.MQConstants;
import com.lantin.unitrade.domain.dto.PayOrderDTO;
import com.lantin.unitrade.domain.po.Order;
import com.lantin.unitrade.enums.OrderStatus;
import com.lantin.unitrade.enums.PayStatus;
import com.lantin.unitrade.service.IOrderService;
import com.lantin.unitrade.service.IPayOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import static com.lantin.unitrade.constant.MQConstants.*;

/**
 * 订单服务的延迟消息监听器
 * 当订单创建成功后，会发送延迟消息，该监听器监听到延迟消息后会主动向支付服务查询支付状态
 * @Author lantin
 * @Date 2024/8/1
 */

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderDelayMessageListener {

    private final IOrderService orderService;
    private final IPayOrderService payOrderService;

    /**
     * 监听延迟消息，主动向支付服务查询支付状态
     * @param orderId
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = DELAY_ORDER_QUEUE),  // 队列
            exchange = @Exchange(name = DELAY_EXCHANGE, delayed = "true"), // 延迟交换机，当消息投递到交换机后会暂存一段时间，到期后再投递到队列
            key = DELAY_ORDER_KEY // routing key
    ))
    public void listenOrderDelayMessage(Long orderId) {
        log.info("已监听到延迟消息，准备确认支付状态...");
        // 1. 查询数据库中的该订单
        Order order = orderService.getById(orderId);
        // 2. 检测订单状态，判断是否已支付
        if (order == null || !OrderStatus.UNPAID.equalsValue(order.getStatus())) {
            // 订单不存在或已经支付，直接结束（可能之前就已经支付完成业务单被改成后续状态）
            return;
        }
        // 3. 未支付，需要主动向支付服务查询支付流水状态,如果支付单
        PayOrderDTO payOrder = payOrderService.queryPayOrderByBizOrderNo(orderId);
        // 4. 判断是否支付
        if (payOrder != null && PayStatus.TRADE_SUCCESS.equalsValue(payOrder.getStatus())) {
            // 4.1 已支付（只是没有收到支付服务发来的消息），更新业务单状态为已支付
            orderService.markOrderPaySuccess(orderId);
        } else {
            // 4.2 未支付，取消订单，恢复库存
            orderService.cancelOrder(orderId);
        }
    }
}
