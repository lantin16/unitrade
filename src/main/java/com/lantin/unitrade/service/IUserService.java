package com.lantin.unitrade.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lantin.unitrade.domain.dto.LoginFormDTO;
import com.lantin.unitrade.domain.dto.Result;
import com.lantin.unitrade.domain.po.User;
import javax.servlet.http.HttpSession;


public interface IUserService extends IService<User> {

    Result sendCode(String phone, HttpSession session);

    Result login(LoginFormDTO loginForm, HttpSession session);

    Result sign();

    Result signCount();

    Result getUserInfo(Long userId);

    void deductMoney(String pw, Integer totalFee);

    void updateUser(User user);
}
