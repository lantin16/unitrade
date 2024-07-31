package com.lantin.unitrade.service.impl;

import cn.hutool.core.lang.UUID;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lantin.unitrade.domain.dto.PayApplyDTO;
import com.lantin.unitrade.domain.dto.PayOrderFormDTO;
import com.lantin.unitrade.domain.po.PayOrder;
import com.lantin.unitrade.enums.PayStatus;
import com.lantin.unitrade.exception.BizIllegalException;
import com.lantin.unitrade.mapper.PayOrderMapper;
import com.lantin.unitrade.service.IPayOrderService;
import com.lantin.unitrade.utils.BeanUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.concurrent.ListenableFutureCallback;

import java.time.LocalDateTime;


@Slf4j
@Service
@RequiredArgsConstructor
public class PayOrderServiceImpl extends ServiceImpl<PayOrderMapper, PayOrder> implements IPayOrderService {

    private final UserClient userClient;

    // private final TradeClient tradeClient;

    private final RabbitTemplate rabbitTemplate;

    @Override
    public String applyPayOrder(PayApplyDTO applyDTO) {
        // 1.幂等性校验
        PayOrder payOrder = checkIdempotent(applyDTO);
        // 2.返回结果
        return payOrder.getId().toString();
    }

    /**
     * 需要跨服务则利用FeignClient向其他微服务的controller发送请求
     * @param payOrderFormDTO
     */
    @Override
    @GlobalTransactional
    public void tryPayOrderByBalance(PayOrderFormDTO payOrderFormDTO) {
        // 1.查询支付单
        PayOrder po = getById(payOrderFormDTO.getId());
        // 2.判断状态
        if(!PayStatus.WAIT_BUYER_PAY.equalsValue(po.getStatus())){
            // 订单不是未支付，状态异常
            throw new BizIllegalException("交易已支付或关闭！");
        }
        // 3.尝试扣减余额
        userClient.deductUserMoney(payOrderFormDTO.getPw(), po.getAmount());
        // 4.修改支付单状态
        boolean success = markPayOrderSuccess(payOrderFormDTO.getId(), LocalDateTime.now());
        if (!success) {
            throw new BizIllegalException("交易已支付或关闭！");
        }

        // TODO 测试完延迟消息后解除注释继续测试
        // 5.修改订单状态
        // tradeClient.markOrderPaySuccess(po.getBizOrderNo()); // 不再是同步调用，而是用消息队列进行异步通信
        // 发消息这种异步通信最好是不要对原有业务产生影响，因此try起来
        try {

            // 由于每个消息发送时的处理逻辑不一定相同，因此ConfirmCallback需要在每次发消息时定义。
            // 创建CorrelationData
            CorrelationData cd = new CorrelationData(UUID.randomUUID().toString(true));
            // 给Future添加ConfirmCallback
            cd.getFuture().addCallback(new ListenableFutureCallback<CorrelationData.Confirm>() {
                @Override
                public void onFailure(Throwable ex) {
                    // Future发生异常时的处理逻辑，基本不会触发
                    log.error("spring amqp 处理确认结果异常", ex);
                }

                @Override
                public void onSuccess(CorrelationData.Confirm result) {
                    // Future接收到回执的处理逻辑，参数中的result就是回执内容
                    if (result.isAck()) {   // mq返回的是ack，代表投递成功
                        log.debug("收到ConfirmCallback ack，消息发送成功！");
                    } else {    // mq返回的是nack，代表投递失败
                        log.error("收到ConfirmCallback nack，消息发送失败！reason：{}", result.getReason());
                        // TODO 需要进行消息重发
                    }
                }
            });

            rabbitTemplate.convertAndSend("pay.direct", "pay.success", po.getBizOrderNo(), cd); // 消息就是订单id
        } catch (AmqpException e) {
            log.error("发送订单状态变更通知失败，订单id：{}", po.getBizOrderNo());
        }

    }

    @Override
    @Transactional
    public void updateStatusByOrderId(Long orderId, Integer status) {
        lambdaUpdate()
                .set(PayOrder::getStatus, status)
                .eq(PayOrder::getBizOrderNo, orderId)
                .update();
    }

    public boolean markPayOrderSuccess(Long id, LocalDateTime successTime) {
        return lambdaUpdate()
                .set(PayOrder::getStatus, PayStatus.TRADE_SUCCESS.getValue())
                .set(PayOrder::getPaySuccessTime, successTime)
                .eq(PayOrder::getId, id)
                // 支付状态的乐观锁判断
                .in(PayOrder::getStatus, PayStatus.NOT_COMMIT.getValue(), PayStatus.WAIT_BUYER_PAY.getValue())
                .update();
    }


    private PayOrder checkIdempotent(PayApplyDTO applyDTO) {
        // 1.首先查询支付单
        PayOrder oldOrder = queryByBizOrderNo(applyDTO.getBizOrderNo());
        // 2.判断是否存在
        if (oldOrder == null) {
            // 不存在支付单，说明是第一次，写入新的支付单并返回
            PayOrder payOrder = buildPayOrder(applyDTO);
            payOrder.setPayOrderNo(IdWorker.getId());
            save(payOrder);
            return payOrder;
        }
        // 3.旧单已经存在，判断是否支付成功
        if (PayStatus.TRADE_SUCCESS.equalsValue(oldOrder.getStatus())) {
            // 已经支付成功，抛出异常
            throw new BizIllegalException("订单已经支付！");
        }
        // 4.旧单已经存在，判断是否已经关闭
        if (PayStatus.TRADE_CLOSED.equalsValue(oldOrder.getStatus())) {
            // 已经关闭，抛出异常
            throw new BizIllegalException("订单已关闭");
        }
        // 5.旧单已经存在，判断支付渠道是否一致
        if (!StringUtils.equals(oldOrder.getPayChannelCode(), applyDTO.getPayChannelCode())) {
            // 支付渠道不一致，需要重置数据，然后重新申请支付单
            PayOrder payOrder = buildPayOrder(applyDTO);
            payOrder.setId(oldOrder.getId());
            payOrder.setQrCodeUrl("");
            updateById(payOrder);
            payOrder.setPayOrderNo(oldOrder.getPayOrderNo());
            return payOrder;
        }
        // 6.旧单已经存在，且可能是未支付或未提交，且支付渠道一致，直接返回旧数据
        return oldOrder;
    }

    private PayOrder buildPayOrder(PayApplyDTO payApplyDTO) {
        // 1.数据转换
        PayOrder payOrder = BeanUtils.toBean(payApplyDTO, PayOrder.class);
        // 2.初始化数据
        payOrder.setPayOverTime(LocalDateTime.now().plusMinutes(120L));
        payOrder.setStatus(PayStatus.WAIT_BUYER_PAY.getValue());
        payOrder.setBizUserId(UserContext.getUser());
        return payOrder;
    }
    public PayOrder queryByBizOrderNo(Long bizOrderNo) {
        return lambdaQuery()
                .eq(PayOrder::getBizOrderNo, bizOrderNo)
                .one();
    }
}
