package com.lantin.unitrade.utils;


import com.lantin.unitrade.domain.dto.UserDTO;

/**
 * 用户登录后，其信息会保存到ThreadLocal中，方便后续业务获取
 * @Author lantin
 * @Date 2024/7/30
 */

public class UserHolder {
    private static final ThreadLocal<UserDTO> tl = new ThreadLocal<>();

    public static void saveUser(UserDTO user){
        tl.set(user);
    }

    public static UserDTO getUser(){
        return tl.get();
    }

    public static void removeUser(){
        tl.remove();
    }
}
