package com.lantin.unitrade.domain.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 商品 DTO
 * @Author lantin
 * @Date 2024/7/30
 */

@Data
@ApiModel(description = "商品实体")
public class ItemDTO {
    @ApiModelProperty("商品id")
    private Long id;
    @ApiModelProperty("名称")
    private String name;
    @ApiModelProperty("价格（分）")
    private Integer price;
    @ApiModelProperty("库存数量")
    private Integer stock;
    @ApiModelProperty("商品图片")
    private String image;
    @ApiModelProperty("商品介绍")
    private String introduce;
    @ApiModelProperty("类目名称")
    private String category;
    @ApiModelProperty("品牌名称")
    private String brand;
    @ApiModelProperty("规格")
    private String spec;
    @ApiModelProperty("评论数")
    private Integer commentCount;
    @ApiModelProperty("商品状态 1-在售，2-交易中，3-交易完成，4-已下架")
    private Integer status;
}
