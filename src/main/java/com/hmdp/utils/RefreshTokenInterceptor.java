package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.UserDTO;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;

@Component
public class RefreshTokenInterceptor implements HandlerInterceptor {
    private JwtUtils jwtUtils;

    public RefreshTokenInterceptor(JwtUtils jwtUtils) {
        this.jwtUtils = jwtUtils;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String token = request.getHeader("authorization");
        if (token == null || token.isEmpty()) {
            return true;
        }
        try {
            Claims claims = jwtUtils.parseToken(token);
            Long userId = Long.valueOf(claims.getSubject());
            Map<String, Object> map = BeanUtil.beanToMap(claims, false, false);
            UserDTO userDTO = BeanUtil.fillBeanWithMap(map, new UserDTO(), false);
            userDTO.setId(userId);
            UserHolder.saveUser(userDTO);
        } catch (Exception e) {
            // Token 解析失败，不拦截，由 LoginInterceptor 处理
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}
