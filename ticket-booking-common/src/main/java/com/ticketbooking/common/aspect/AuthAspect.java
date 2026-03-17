package com.ticketbooking.common.aspect;

import cn.hutool.json.JSONUtil;
import com.ticketbooking.common.annotation.RequireAuth;
import com.ticketbooking.common.constant.JwtConstants;
import com.ticketbooking.common.context.UserContext;
import com.ticketbooking.common.context.UserInfo;
import com.ticketbooking.common.enums.ErrorCode;
import com.ticketbooking.common.enums.Role;
import com.ticketbooking.common.result.Result;
import com.ticketbooking.common.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
@Order(1) // 优先级最高：先鉴权，再执行限流切面
@RequiredArgsConstructor
public class AuthAspect {

    private final JwtUtils jwtUtils;

    @Around("@within(requireAuth) || @annotation(requireAuth)")
    public Object authCheck(ProceedingJoinPoint joinPoint, RequireAuth requireAuth) throws Throwable {
        // 1. 获取请求对象（AOP 通用获取方式）
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return joinPoint.proceed();
        }
        HttpServletRequest request = attributes.getRequest();
        HttpServletResponse response = attributes.getResponse();

        // 获取注解上的角色要求
        Role[] requiredRoles = requireAuth.value();
        boolean requireAdmin = false;
        for (Role role : requiredRoles) {
            if (role == Role.ADMIN) {
                requireAdmin = true;
                break;
            }
        }

        // 优先读取网关透传的 Header
        String userIdHeader = request.getHeader(JwtConstants.HEADER_USER_ID);
        String usernameHeader = request.getHeader(JwtConstants.HEADER_USERNAME);
        String isAdminHeader = request.getHeader(JwtConstants.HEADER_IS_ADMIN);

        if (userIdHeader != null && usernameHeader != null) {
            // 检查管理员权限
            if (requireAdmin && !"true".equals(isAdminHeader)) {
                return sendForbiddenResult(response);
            }

            Long userId = Long.parseLong(userIdHeader);
            UserInfo userInfo = UserInfo.builder()
                    .userId(userId)
                    .username(usernameHeader)
                    .build();
            UserContext.setUserInfo(userInfo);
            return joinPoint.proceed();
        }

        // 无透传Header，解析Token
        String authHeader = request.getHeader(JwtConstants.HEADER_AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith(JwtConstants.TOKEN_PREFIX)) {
            return sendUnauthorizedResult(response);
        }

        String token = authHeader.substring(JwtConstants.TOKEN_PREFIX.length());
        Claims claims = jwtUtils.validateToken(token);
        if (claims == null) {
            return sendUnauthorizedResult(response);
        }

        // 检查管理员权限（从Token中获取）
        if (requireAdmin && !jwtUtils.isAdmin(claims)) {
            return sendForbiddenResult(response);
        }

        // 封装用户信息
        Long userId = jwtUtils.getUserId(claims);
        String username = jwtUtils.getUsername(claims);
        UserInfo userInfo = UserInfo.builder()
                .userId(userId)
                .username(username)
                .token(token)
                .build();
        UserContext.setUserInfo(userInfo);

        // 2. 鉴权通过，执行接口逻辑
        return joinPoint.proceed();
    }

    private String sendUnauthorizedResult(HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        Result<Void> result = Result.error(ErrorCode.UNAUTHORIZED);
        return JSONUtil.toJsonStr(result);
    }

    private String sendForbiddenResult(HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        Result<Void> result = Result.error(ErrorCode.FORBIDDEN);
        return JSONUtil.toJsonStr(result);
    }
}
