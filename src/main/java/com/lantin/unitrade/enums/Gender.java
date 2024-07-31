package com.lantin.unitrade.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.lantin.unitrade.exception.BadRequestException;
import lombok.Getter;

/**
 * 用户性别枚举
 * @Author lantin
 * @Date 2024/7/30
 */

@Getter
public enum Gender {
    UNKNOWN(0, "未知"),
    MALE(1, "男"),
    FEMALE(2, "女"),
    ;
    @EnumValue  // 使得 MyBatis-Plus 在进行数据库操作时使用该字段的值。
    int value;
    String desc;

    Gender(Integer value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    public static Gender of(int value) {
        if (value == 0) {
            return UNKNOWN;
        }
        if (value == 1) {
            return MALE;
        }
        if (value == 2) {
            return FEMALE;
        }
        throw new BadRequestException("用户性别错误");
    }

}