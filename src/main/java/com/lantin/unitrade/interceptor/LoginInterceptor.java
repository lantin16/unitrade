package com.lantin.unitrade.interceptor;

import com.lantin.unitrade.utils.UserHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 登录拦截器
 * 在RefreshTokenInterceptor后面拦截需要登陆才能进行的请求，只有登录了才放行（上一个拦截器取出了用户且存到了ThreadLocal）
 * @Author lantin
 * @Date 2024/7/30
 */

@Component
public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 判断是否需要拦截（ThreadLocal中是否有用户信息），如果用户你没登陆就需要拦截
        if (UserHolder.getUser() == null) {
            // 如果没有就拦截
            response.setStatus(401);
            return false;
        }
        // 如果有就放行
        return true;
    }


    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        HandlerInterceptor.super.afterCompletion(request, response, handler, ex);
    }
}
