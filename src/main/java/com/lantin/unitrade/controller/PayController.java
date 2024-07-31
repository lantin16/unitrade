package com.lantin.unitrade.controller;

import com.lantin.unitrade.domain.dto.PayApplyDTO;
import com.lantin.unitrade.domain.dto.PayOrderDTO;
import com.lantin.unitrade.domain.dto.PayOrderFormDTO;
import com.lantin.unitrade.domain.po.PayOrder;
import com.lantin.unitrade.domain.vo.PayOrderVO;
import com.lantin.unitrade.enums.PayType;
import com.lantin.unitrade.exception.BizIllegalException;
import com.lantin.unitrade.service.IPayOrderService;
import com.lantin.unitrade.utils.BeanUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Api(tags = "支付相关接口")
@RestController
@RequestMapping("pay-orders")
@RequiredArgsConstructor
public class PayController {

    private final IPayOrderService payOrderService;

    @ApiOperation("查询支付单")
    @GetMapping
    public List<PayOrderVO> queryPayOrders(){
        return BeanUtils.copyList(payOrderService.list(), PayOrderVO.class);
    }

    @ApiOperation("生成支付单")
    @PostMapping
    public String applyPayOrder(@RequestBody PayApplyDTO applyDTO){
        if(!PayType.BALANCE.equalsValue(applyDTO.getPayType())){
            // 目前只支持余额支付
            throw new BizIllegalException("抱歉，目前只支持余额支付");
        }
        return payOrderService.applyPayOrder(applyDTO);
    }

    @ApiOperation("尝试基于用户余额支付")
    @ApiImplicitParam(value = "支付单id", name = "id")
    @PostMapping("{id}")
    public void tryPayOrderByBalance(@PathVariable("id") Long id, @RequestBody PayOrderFormDTO payOrderFormDTO){
        payOrderFormDTO.setId(id);
        payOrderService.tryPayOrderByBalance(payOrderFormDTO);
    }

    @ApiOperation("根据id查询支付单")
    @GetMapping("/biz/{id}")
    public PayOrderDTO queryPayOrderByBizOrderNo(@PathVariable("id") Long id){
        PayOrder payOrder = payOrderService.lambdaQuery().eq(PayOrder::getBizOrderNo, id).one();
        return BeanUtils.copyBean(payOrder, PayOrderDTO.class);
    }


    @ApiOperation("修改订单支付状态")
    @PutMapping("/status/{oid}/{status}")
    public void updatePayStatusByBizOrderNo(@PathVariable("oid") Long orderId,
                                            @PathVariable("status") Integer status) {
        payOrderService.updateStatusByOrderId(orderId, status);
    }
}
