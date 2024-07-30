package com.lantin.unitrade.controller;


import cn.hutool.core.bean.BeanUtil;
import com.lantin.unitrade.domain.dto.LoginFormDTO;
import com.lantin.unitrade.domain.dto.Result;
import com.lantin.unitrade.domain.dto.UserDTO;
import com.lantin.unitrade.domain.po.User;
import com.lantin.unitrade.domain.po.UserInfo;
import com.lantin.unitrade.service.IUserInfoService;
import com.lantin.unitrade.service.IUserService;
import com.lantin.unitrade.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;


@Slf4j
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final IUserService userService;

    private final IUserInfoService userInfoService;

    /**
     * 发送手机验证码
     * @param phone 手机号
     * @param session
     * @return
     */
    @PostMapping("/code")
    public Result sendCode(@RequestParam("phone") String phone, HttpSession session) {
        // 发送短信验证码并保存验证码
        return userService.sendCode(phone, session);
    }

    /**
     * 登录功能
     * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
     */
    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginForm, HttpSession session){
        // 实现登录功能
        return userService.login(loginForm, session);
    }

    /**
     * 登出功能
     * @return 无
     */
    @PostMapping("/logout")
    public Result logout(){
        // TODO 实现登出功能
        return Result.fail("功能未完成");
    }

    @GetMapping("/me")
    public Result me(){
        // 获取当前登录的用户并返回
        UserDTO user = UserHolder.getUser();
        return Result.ok(user);
    }


    /**
     * 查询用户全部信息
     * 当用户自己修改个人信息时用到
     * @param userId
     * @return
     */
    @GetMapping("/{id}")
    public Result queryUserById(@PathVariable("id") Long userId){
        // 返回
        return Result.ok(userService.getById(userId));
    }


    /**
     * 查询用户概览信息
     * 在展示用户评论或帖子时用到，仅包含用户id、昵称、头像
     * @param userId
     * @return
     */
    @GetMapping("/overview/{id}")
    public Result queryOverviewUserById(@PathVariable("id") Long userId){
        // 查询全部信息
        User user = userService.getById(userId);
        if (user == null) {
            return Result.ok();
        }
        // 只将对应信息封装到 UserDTO 中返回
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        // 返回
        return Result.ok(userDTO);
    }


    /**
     * 查询用户个人信息
     * 当查看他人用户主页时用到，不包含隐私信息
     * @param userId
     * @return
     */
    @GetMapping("/info/{id}")
    public Result queryUserInfoById(@PathVariable("id") Long userId){
        return userService.getUserInfo(userId);
    }


    /**
     * 用户签到
     * @return
     */
    @PostMapping("/sign")
    public Result sign() {
        return userService.sign();
    }


    /**
     * 统计当前用户截至当前时间在本月的连续签到天数
     * @return
     */
    @GetMapping("/sign/count")
    public Result signCount() {
        return userService.signCount();
    }

}
