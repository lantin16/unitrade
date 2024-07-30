package com.lantin.unitrade.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 收货地址表
 * 因为本平台仅限校内师生使用，范围较小，因此地址只用一个字段存储
 * @Author lantin
 * @Date 2024/7/30
 */

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("address")
public class Address implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 详细地址
     */
    private String address;

    /**
     * 手机
     */
    private String mobile;

    /**
     * 联系人姓名
     */
    private String contact;

    /**
     * 是否是默认 1默认 0否
     */
    private Integer isDefault;

    /**
     * 备注
     */
    private String notes;


}
