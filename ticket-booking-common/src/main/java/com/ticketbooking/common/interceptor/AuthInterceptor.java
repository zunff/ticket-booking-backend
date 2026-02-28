package com.ticketbooking.common.interceptor;

import com.ticketbooking.common.annotation.RequireAuth;
import com.ticketbooking.common.context.UserContext;
import com.ticketbooking.common.context.UserInfo;
import com.ticketbooking.common.utils.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {
    
    private final JwtUtils jwtUtils;
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        
        HandlerMethod handlerMethod = (HandlerMethod) handler;
        RequireAuth requireAuth = handlerMethod.getMethodAnnotation(RequireAuth.class);
        if (requireAuth == null) {
            requireAuth = handlerMethod.getBeanType().getAnnotation(RequireAuth.class);
        }
        
        if (requireAuth == null || !requireAuth.required()) {
            return true;
        }
        
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendUnauthorizedResponse(response);
            return false;
        }
        
        String token = authHeader.substring(7);
        if (!jwtUtils.validateToken(token)) {
            sendUnauthorizedResponse(response);
            return false;
        }
        
        Long userId = jwtUtils.getUserId(token);
        String username = jwtUtils.getUsername(token);
        
        UserInfo userInfo = UserInfo.builder()
                .userId(userId)
                .username(username)
                .token(token)
                .build();
        UserContext.setUserInfo(userInfo);
        
        return true;
    }
    
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }
    
    private void sendUnauthorizedResponse(HttpServletResponse response) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":401,\"message\":\"未登录或Token已过期\",\"success\":false}");
    }
}
