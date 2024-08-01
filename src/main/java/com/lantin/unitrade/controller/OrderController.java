package com.lantin.unitrade.controller;

import com.lantin.unitrade.domain.dto.OrderFormDTO;
import com.lantin.unitrade.domain.vo.OrderVO;
import com.lantin.unitrade.service.IOrderService;
import com.lantin.unitrade.utils.BeanUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.annotations.Param;
import org.springframework.web.bind.annotation.*;

@Api(tags = "订单管理接口")
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {
    private final IOrderService orderService;

    @ApiOperation("根据id查询订单")
    @GetMapping("{id}")
    public OrderVO queryOrderById(@Param ("订单id")@PathVariable("id") Long orderId) {
        return BeanUtils.copyBean(orderService.getById(orderId), OrderVO.class);
    }

    /**
     * 下单，具体是否能下单成功需要后续判断（库存等）
     * @param orderFormDTO
     * @return
     */
    @ApiOperation("下单")
    @PostMapping
    public Long placeOrder(@RequestBody OrderFormDTO orderFormDTO){
        return orderService.placeOrder(orderFormDTO);
    }

    /**
     * TODO 要不要改成修改订单状态而不是限定改成已支付？
     * @param orderId
     */
    @ApiOperation("标记订单已支付")
    @ApiImplicitParam(name = "orderId", value = "订单id", paramType = "path")
    @PutMapping("/{orderId}")
    public void markOrderPaySuccess(@PathVariable("orderId") Long orderId) {
        orderService.markOrderPaySuccess(orderId);
    }

}
