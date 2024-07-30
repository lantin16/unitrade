package com.lantin.unitrade.domain.dto;

import lombok.Data;

/**
 * 用户DTO
 * 只包含id，昵称和头像
 * @Author lantin
 * @Date 2024/7/30
 */

@Data
public class UserDTO {
    private Long id;
    private String nickName;
    private String icon;
}
