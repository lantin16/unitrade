package com.lantin.unitrade.utils;


import cn.hutool.core.util.RandomUtil;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;

/**
 * 密码加密工具类
 * @Author lantin
 * @Date 2024/7/30
 */

public class PasswordEncoder {

    /**
     * 对密码加密（生成随机盐）
     * @param password
     * @return
     */
    public static String encode(String password) {
        // 生成盐
        String salt = RandomUtil.randomString(20);
        // 加密
        return encode(password,salt);
    }

    /**
     * 对密码加密（指定盐）
     * @param password
     * @param salt
     * @return
     */
    public static String encode(String password, String salt) {
        // 加密：生成盐 + @ + md5(密码 + 盐)
        return salt + "@" + DigestUtils.md5DigestAsHex((password + salt).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 比较密码是否匹配
     * @param encodedPassword
     * @param rawPassword
     * @return
     */
    public static Boolean matches(String encodedPassword, String rawPassword) {
        if (encodedPassword == null || rawPassword == null) {
            return false;
        }
        if(!encodedPassword.contains("@")){
            throw new RuntimeException("密码格式不正确！");
        }
        String[] arr = encodedPassword.split("@");
        // 获取盐
        String salt = arr[0];
        // 比较
        return encodedPassword.equals(encode(rawPassword, salt));
    }
}
