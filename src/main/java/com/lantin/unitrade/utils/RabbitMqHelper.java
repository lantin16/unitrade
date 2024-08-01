package com.lantin.unitrade.utils;

import cn.hutool.core.lang.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.concurrent.ListenableFutureCallback;

/**
 * 发送消息的工具类
 */

@Slf4j
@RequiredArgsConstructor
@Component
public class RabbitMqHelper {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 发送普通消息
     * @param exchange
     * @param routingKey
     * @param msg
     * @param messagePostProcessor 消息后处理器
     */
    public void sendMessage(String exchange, String routingKey, Object msg, MessagePostProcessor messagePostProcessor){
        log.debug("准备发送消息，exchange:{}, routingKey:{}, msg:{}", exchange, routingKey, msg);
        rabbitTemplate.convertAndSend(exchange, routingKey, msg, messagePostProcessor);
    }

    /**
     * 发送延迟消息
     * @param exchange
     * @param routingKey
     * @param msg
     * @param delay 延迟时间，单位毫秒
     */
    public void sendDelayMessage(String exchange, String routingKey, Object msg, int delay){
        rabbitTemplate.convertAndSend(
                exchange,
                routingKey,
                msg,
                message -> {    // 利用消息后处理器在消息头中添加延迟消息属性
                    message.getMessageProperties().setDelay(delay);
                    return message;
                }
        );
    }

    /**
     * 开启生产者确认机制后如果mq返回nack可以有限次数重试，若依然失败则记录异常信息
     * 只要消息到达了交换机都返回ack，其余情况都是nack
     * @param exchange
     * @param routingKey
     * @param msg
     * @param messagePostProcessor 消息后处理器
     * @param maxRetries 最大重试次数
     */
    public void sendMessageWithConfirm(String exchange, String routingKey, Object msg, MessagePostProcessor messagePostProcessor, int maxRetries){
        log.debug("准备发送消息，exchange:{}, routingKey:{}, msg:{}", exchange, routingKey, msg);

        CorrelationData cd = new CorrelationData(UUID.randomUUID().toString(true));
        // 给CorrelationData的Future添加异步的回调函数来处理消息回执
        cd.getFuture().addCallback(new ListenableFutureCallback<CorrelationData.Confirm>() {
            int retryCount;
            @Override
            public void onFailure(Throwable ex) {
                // Future发生异常时的处理逻辑，基本不会触发
                log.error("spring amqp 处理确认结果异常", ex);
            }

            @Override
            public void onSuccess(CorrelationData.Confirm result) {
                if (result != null && !result.isAck()) {
                    log.debug("消息发送失败，收到nack，已重试次数：{}", retryCount);
                    if (retryCount >= maxRetries) {
                        log.error("消息发送重试次数耗尽，发送失败, exchange:{}, routingKey:{}, msg:{}", exchange, routingKey, msg);
                        return;
                    }
                    // 重试发送
                    CorrelationData cd = new CorrelationData(UUID.randomUUID().toString(true));
                    cd.getFuture().addCallback(this);   // 这里直接将这个ListenableFutureCallback对象传进去
                    if (messagePostProcessor == null) {
                        rabbitTemplate.convertAndSend(exchange, routingKey, msg, cd);
                    } else {
                        rabbitTemplate.convertAndSend(exchange, routingKey, msg, messagePostProcessor, cd);
                    }
                    retryCount++;   // 重试次数加1
                }
            }
        });

        if (messagePostProcessor == null) {
            rabbitTemplate.convertAndSend(exchange, routingKey, msg, cd);
        } else {
            rabbitTemplate.convertAndSend(exchange, routingKey, msg, messagePostProcessor, cd);
        }
    }
}
