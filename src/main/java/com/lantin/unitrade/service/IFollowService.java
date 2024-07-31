package com.lantin.unitrade.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lantin.unitrade.domain.dto.Result;
import com.lantin.unitrade.domain.po.Follow;


public interface IFollowService extends IService<Follow> {

    Result follow(Long followUserId, Boolean isFollow);

    Result followOrNot(Long followUserId);

    Result followCommons(Long id);
}
