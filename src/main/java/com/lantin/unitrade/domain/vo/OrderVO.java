package com.lantin.unitrade.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 业务单VO
 * @Author lantin
 * @Date 2024/7/31
 */

@Data
@ApiModel(description = "订单页面VO")
public class OrderVO {
    @ApiModelProperty("订单id")
    private Long id;
    @ApiModelProperty("总金额，单位为分")
    private Integer totalFee;
    @ApiModelProperty("支付类型，1、支付宝，2、微信，3、扣减余额")
    private Integer paymentType;
    @ApiModelProperty("用户id")
    private Long userId;
    @ApiModelProperty("订单的状态，1.未付款 2.已付款 3.交易完成 4.交易取消")
    private Integer status;
    @ApiModelProperty("创建时间")
    private LocalDateTime createTime;
    @ApiModelProperty("支付时间")
    private LocalDateTime payTime;
    @ApiModelProperty("交易完成时间")
    private LocalDateTime endTime;
    @ApiModelProperty("交易关闭时间")
    private LocalDateTime closeTime;
}
