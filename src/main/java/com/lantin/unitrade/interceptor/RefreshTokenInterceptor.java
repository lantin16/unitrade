package com.lantin.unitrade.interceptor;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.lantin.unitrade.domain.dto.UserDTO;
import com.lantin.unitrade.constant.RedisConstants;
import com.lantin.unitrade.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 刷新token的拦截器
 * 在前面拦截一切请求，判断token是否存在，存在则刷新token的有效期，但无论用户/token是否存在都会放行到下一个拦截器
 * @Author lantin
 * @Date 2024/7/30
 */

@Component
public class RefreshTokenInterceptor implements HandlerInterceptor {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;


    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 获取请求头中的token
        String token = request.getHeader("authorization");  // 这个authorization是和前端定义的
        if (StrUtil.isBlank(token)) {
            // 不存在，放行到下一个拦截器
            return true;
        }

        // 2. 基于token获取redis中的用户
        String key = RedisConstants.LOGIN_USER_KEY + token;
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(key);

        // 3. 判断用户是否存在
        if (userMap.isEmpty()) {
            // 4. 不存在，放行到下一个拦截器
            return true;
        }

        // 5. 将查询到的Hash数据转为UserDTO对象
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);

        // 6. 存在，说明登录了，保存用户信息到ThreadLocal，以便后面的业务能够获取该用户的信息
        UserHolder.saveUser(userDTO);

        // 7. 刷新token的有效期
        // 因为有效期的逻辑是超过有效期都没访问过才删除该用户数据，如果有效期内该用户又有访问（保持活跃）则刷新有效期
        stringRedisTemplate.expire(key, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);

        // 8. 放行
        return true;
    }


    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // TODO 改进点：离开前删除ThreadLocal中的用户信息，避免内存泄漏，优化可以查看JVM相关部分（加分点！）
        HandlerInterceptor.super.afterCompletion(request, response, handler, ex);
    }
}
