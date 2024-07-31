package com.lantin.unitrade.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lantin.unitrade.enums.Gender;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户个人信息表
 * TODO 啥时候用这个类？暂时准备弃用，可能在他人访问用户主页时设计成一个VO？
 * @Author lantin
 * @Date 2024/7/30
 */

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("user_info")
public class UserInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键，用户id
     */
    @TableId(value = "user_id")
    private Long userId;

    /**
     * 昵称，默认是随机字符
     */
    private String nickName;

    /**
     * 用户头像
     */
    private String icon = "";

    /**
     * 个人介绍，不要超过128个字符
     */
    private String introduce;

    /**
     * 粉丝数量
     */
    private Integer fans;

    /**
     * 关注的人的数量
     */
    private Integer followee;

    /**
     * 性别，0：男，1：女
     */
    private Gender gender;

    /**
     * 生日
     */
    private LocalDate birthday;

    /**
     * 评分，1~5分，乘10保存，避免小数
     */
    private Integer score;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;


}
