package com.lantin.unitrade.service.impl;

import cn.hutool.core.lang.UUID;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lantin.unitrade.domain.dto.PayApplyDTO;
import com.lantin.unitrade.domain.dto.PayOrderDTO;
import com.lantin.unitrade.domain.dto.PayOrderFormDTO;
import com.lantin.unitrade.domain.po.PayOrder;
import com.lantin.unitrade.enums.PayStatus;
import com.lantin.unitrade.exception.BizIllegalException;
import com.lantin.unitrade.mapper.PayOrderMapper;
import com.lantin.unitrade.service.IPayOrderService;
import com.lantin.unitrade.service.IUserService;
import com.lantin.unitrade.utils.BeanUtils;
import com.lantin.unitrade.utils.RabbitMqHelper;
import com.lantin.unitrade.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.concurrent.ListenableFutureCallback;

import java.time.LocalDateTime;

import static com.lantin.unitrade.constant.MQConstants.*;
import static com.lantin.unitrade.constant.MQConstants.ORDER_SUCCESS_ROUTINGKEY;


@Slf4j
@Service
@RequiredArgsConstructor
public class PayOrderServiceImpl extends ServiceImpl<PayOrderMapper, PayOrder> implements IPayOrderService {

    private final IUserService userService;

    private final RabbitTemplate rabbitTemplate;

    private final RabbitMqHelper rabbitMqHelper;

    /**
     * 生成支付单
     * @param applyDTO
     * @return
     */
    @Override
    public String applyPayOrder(PayApplyDTO applyDTO) {
        // 1.幂等性校验
        PayOrder payOrder = checkIdempotent(applyDTO);
        // 2.返回结果
        return payOrder.getId().toString();
    }

    /**
     * 尝试基于用户余额支付，支付成功后修改支付单状态，同时发送消息异步修改业务订单状态（如果消息未能按时到达，业务单的延迟消息也会主动检查支付单状态兜底）
     * @param payOrderFormDTO
     */
    @Override
    @Transactional
    public void tryPayOrderByBalance(PayOrderFormDTO payOrderFormDTO) {
        // 1.查询支付单
        PayOrder po = getById(payOrderFormDTO.getId());
        // 2.判断状态
        if(!PayStatus.WAIT_BUYER_PAY.equalsValue(po.getStatus())){
            // 订单不是未支付，状态异常
            throw new BizIllegalException("交易已支付或关闭！");
        }
        // 3.尝试扣减余额
        userService.deductMoney(payOrderFormDTO.getPw(), po.getAmount());
        // 4.修改支付单状态
        boolean success = markPayOrderSuccess(payOrderFormDTO.getId(), LocalDateTime.now());
        if (!success) {
            throw new BizIllegalException("交易已支付或关闭！");
        }

        // 5.支付成功发送消息异步修改业务订单状态
        // 发消息这种异步通信最好是不要对原有业务产生影响，因此try起来
        rabbitMqHelper.sendMessageWithConfirm(PAY_DIRECT_EXCHANGE, PAY_SUCCESS_ROUTINGKEY, po.getBizOrderNo(), null, 3); // 生产者最多重试三次
    }

    @Override
    @Transactional
    public void updateStatusByOrderId(Long orderId, Integer status) {
        lambdaUpdate()
                .set(PayOrder::getStatus, status)
                .eq(PayOrder::getBizOrderNo, orderId)
                .update();
    }

    /**
     * 根据业务单id查询支付单
     * @param id
     * @return
     */
    @Override
    public PayOrderDTO queryPayOrderByBizOrderNo(Long id) {
        PayOrder payOrder = lambdaQuery().eq(PayOrder::getBizOrderNo, id).one();
        return BeanUtils.copyBean(payOrder, PayOrderDTO.class);
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
        payOrder.setBizUserId(UserHolder.getUser().getId());
        return payOrder;
    }
    public PayOrder queryByBizOrderNo(Long bizOrderNo) {
        return lambdaQuery()
                .eq(PayOrder::getBizOrderNo, bizOrderNo)
                .one();
    }
}
