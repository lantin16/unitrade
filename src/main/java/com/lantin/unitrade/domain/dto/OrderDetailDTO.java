package com.lantin.unitrade.domain.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 订单明细条目
 * @Author lantin
 * @Date 2024/7/30
 */

@ApiModel(description = "订单明细条目")
@Data
@Accessors(chain = true)
public class OrderDetailDTO {
    @ApiModelProperty("商品id")
    private Long itemId;
    @ApiModelProperty("商品购买数量")
    private Integer num;
}
