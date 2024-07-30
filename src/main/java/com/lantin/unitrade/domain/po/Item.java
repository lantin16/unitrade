package com.lantin.unitrade.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lantin.unitrade.enums.ItemStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 商品表
 * TODO 不要广告字段，品牌、类别取舍？考虑
 * @Author lantin
 * @Date 2024/7/30
 */

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("item")
public class Item implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 商品id
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 名称
     */
    private String name;

    /**
     * 价格（分）
     */
    private Integer price;

    /**
     * 库存数量
     */
    private Integer stock;

    /**
     * 商品图片
     */
    private String image;

    /**
     * 商品介绍，不要超过128个字符
     */
    private String introduce;

    /**
     * 类目名称
     */
    private String category;

    /**
     * 品牌名称
     */
    private String brand;

    /**
     * 规格
     */
    private String spec;

    /**
     * 评论数
     */
    private Integer commentCount;


    /**
     * 商品状态 1-在售，2-交易中，3-交易完成，4-已下架
     */
    private ItemStatus status;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 创建人
     */
    private Long creater;

    /**
     * 修改人
     */
    private Long updater;


}
