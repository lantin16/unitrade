package com.lantin.unitrade.domain.po;


import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lantin.unitrade.enums.Gender;
import com.lantin.unitrade.enums.UserStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户表
 * @Author lantin
 * @Date 2024/7/30
 */

@Data
@EqualsAndHashCode(callSuper = false)   // 自动生成 equals()、hashCode() 和 toString() 方法，但不要调用超类的
@Accessors(chain = true)    // 用于自动生成 getter 和 setter 方法。且生成的 setter 方法将返回当前类的实例（即 "this"）。这使得你可以链式调用多个 setter 方法
@TableName("user")
public class User implements Serializable {

    // 显式声明 serialVersionUID 可以避免在类结构变化时引发的不兼容问题。
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 密码，加密存储
     */
    private String password;

    /**
     * 手机号
     */
    private String phone;

    /**
     * 联系方式，如QQ、微信、邮箱等
     */
    private String contact;

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
     * 评价（0~5星）
     */
    private Integer stars;

    /**
     * 使用状态（1正常 0冻结）
     */
    private UserStatus status;

    /**
     * 账户余额
     */
    private Integer balance;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;


}
