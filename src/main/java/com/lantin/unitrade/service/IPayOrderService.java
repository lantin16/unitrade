package com.lantin.unitrade.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lantin.unitrade.domain.dto.PayApplyDTO;
import com.lantin.unitrade.domain.dto.PayOrderFormDTO;
import com.lantin.unitrade.domain.po.PayOrder;


public interface IPayOrderService extends IService<PayOrder> {

    String applyPayOrder(PayApplyDTO applyDTO);

    void tryPayOrderByBalance(PayOrderFormDTO payOrderFormDTO);

    void updateStatusByOrderId(Long orderId, Integer status);
}
