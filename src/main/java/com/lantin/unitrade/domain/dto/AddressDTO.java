package com.lantin.unitrade.domain.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 收货地址 DTO
 * @Author lantin
 * @Date 2024/7/30
 */

@Data
@ApiModel(description = "收货地址实体")
public class AddressDTO {
    @ApiModelProperty("id")
    private Long id;
    @ApiModelProperty("手机")
    private String mobile;
    @ApiModelProperty("详细地址")
    private String address;
    @ApiModelProperty("联系人")
    private String contact;
    @ApiModelProperty("是否是默认 1默认 0否")
    private Integer isDefault;
    @ApiModelProperty("备注")
    private String notes;
}
