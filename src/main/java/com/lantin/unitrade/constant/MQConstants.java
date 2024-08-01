package com.lantin.unitrade.constant;

/**
 * 消息队列相关常量
 * @Author lantin
 * @Date 2024/8/1
 */
public class MQConstants {

    public static final String ORDER_DIRECT_EXCHANGE = "order.direct";
    public static final String ORDER_SUCCESS_QUEUE = "order.success.queue";
    public static final String ORDER_SUCCESS_ROUTINGKEY = "order.success";
    public static final String PAY_DIRECT_EXCHANGE = "pay.direct";
    public static final String PAY_SUCCESS_QUEUE = "pay.success.queue";
    public static final String PAY_SUCCESS_ROUTINGKEY = "pay.success";
    public static final String ORDER_TOPIC_EXCHANGE = "order.topic";
    public static final String CART_CLEAR_QUEUE = "cart.clear.queue";
    public static final String CART_CLEAR_ROUTINGKEY = "order.create";
    public static final  String DELAY_EXCHANGE = "trade.delay.direct";
    public static final  String DELAY_ORDER_QUEUE = "trade.delay.order.queue";
    public static final  String DELAY_ORDER_KEY = "delay.order.query";

}
