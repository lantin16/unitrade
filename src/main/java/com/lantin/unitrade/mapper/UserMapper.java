package com.lantin.unitrade.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lantin.unitrade.domain.po.User;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;


public interface UserMapper extends BaseMapper<User> {
    @Update("update user set balance = balance - ${totalFee} where id = #{userId}")
    void updateMoney(@Param("userId") Long userId, @Param("totalFee") Integer totalFee);
}
