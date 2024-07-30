package com.lantin.unitrade.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.lantin.unitrade.domain.po.UserInfo;
import com.lantin.unitrade.mapper.UserInfoMapper;
import com.lantin.unitrade.service.IUserInfoService;
import org.springframework.stereotype.Service;


@Service
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo> implements IUserInfoService {

}
