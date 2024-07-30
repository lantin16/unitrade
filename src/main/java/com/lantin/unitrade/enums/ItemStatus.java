package com.lantin.unitrade.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.lantin.unitrade.domain.po.Item;
import com.lantin.unitrade.exception.BadRequestException;
import lombok.Getter;

/**
 * 商品状态枚举
 * @Author lantin
 * @Date 2024/7/30
 */

@Getter
public enum ItemStatus {
    SALE(1, "在售"),
    TRANSACTION(2, "交易中"),
    FINISH(3, "交易完成"),
    REMOVE(4, "已下架"),
    ;
    @EnumValue  // 使得 MyBatis-Plus 在进行数据库操作时使用该字段的值。
    int value;
    String desc;

    ItemStatus(Integer value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    public static ItemStatus of(int value) {
        switch (value) {
            case 1:
                return SALE;
            case 2:
                return TRANSACTION;
            case 3:
                return FINISH;
            case 4:
                return REMOVE;
            default:
                throw new BadRequestException("商品状态错误");
        }
    }
}