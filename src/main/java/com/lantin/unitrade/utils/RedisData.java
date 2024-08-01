package com.lantin.unitrade.utils;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 封装实体类，便于设置逻辑过期时间，且不侵入原代码
 * @Author lantin
 * @Date 2024/7/31
 */

@Data
public class RedisData {
    private LocalDateTime expireTime;   // 逻辑过期时间
    private Object data;
}
