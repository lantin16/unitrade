package com.lantin.unitrade.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.lantin.unitrade.exception.BadRequestException;
import lombok.Getter;

/**
 * 商品状态枚举
 * @Author lantin
 * @Date 2024/7/30
 */

@Getter
public enum OrderStatus {
    UNPAID(1, "未付款"),
    PAID(2, "已付款"),
    FINISH(3, "交易完成"),
    CANCEL(4, "交易取消"),
    ;
    @EnumValue  // 使得 MyBatis-Plus 在进行数据库操作时使用该字段的值。
    int value;
    String desc;

    OrderStatus(Integer value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    public static OrderStatus of(int value) {
        switch (value) {
            case 1:
                return UNPAID;
            case 2:
                return PAID;
            case 3:
                return FINISH;
            case 4:
                return CANCEL;
            default:
                throw new BadRequestException("订单状态错误");
        }
    }

    public boolean equalsValue(Integer value){
        if (value == null) {
            return false;
        }
        return getValue() == value;
    }
}