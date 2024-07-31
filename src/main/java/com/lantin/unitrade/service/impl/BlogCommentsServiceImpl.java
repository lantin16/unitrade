package com.lantin.unitrade.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.lantin.unitrade.domain.po.BlogComments;
import com.lantin.unitrade.mapper.BlogCommentsMapper;
import com.lantin.unitrade.service.IBlogCommentsService;
import org.springframework.stereotype.Service;


@Service
public class BlogCommentsServiceImpl extends ServiceImpl<BlogCommentsMapper, BlogComments> implements IBlogCommentsService {

}
