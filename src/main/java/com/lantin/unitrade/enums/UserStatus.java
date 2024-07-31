package com.lantin.unitrade.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.lantin.unitrade.exception.BadRequestException;
import lombok.Getter;

/**
 * 用户状态枚举
 * @Author lantin
 * @Date 2024/7/30
 */

@Getter
public enum UserStatus {
    FROZEN(0, "冻结"),
    NORMAL(1, "正常"),
    ;
    @EnumValue  // 使得 MyBatis-Plus 在进行数据库操作时使用该字段的值。
    int value;
    String desc;

    UserStatus(Integer value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    public static UserStatus of(int value) {
        if (value == 0) {
            return FROZEN;
        }
        if (value == 1) {
            return NORMAL;
        }
        throw new BadRequestException("账户状态错误");
    }

    public boolean equalsValue(Integer value){
        if (value == null) {
            return false;
        }
        return getValue() == value;
    }
}