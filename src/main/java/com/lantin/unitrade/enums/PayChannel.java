package com.lantin.unitrade.enums;

import cn.hutool.core.util.StrUtil;
import lombok.Getter;

/**
 * 支付渠道枚举类
 * @Author lantin
 * @Date 2024/7/31
 */

@Getter
public enum PayChannel {
    wxPay("微信支付"),
    aliPay("支付宝支付"),
    balance("余额支付"),
    ;

    private final String desc;

    PayChannel(String desc) {
        this.desc = desc;
    }

    public static String desc(String value){
        if (StrUtil.isBlank(value)) {
            return "";
        }
        return PayChannel.valueOf(value).getDesc();
    }
}
