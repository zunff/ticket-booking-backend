package com.ticketbooking.common.interceptor;

import com.ticketbooking.common.annotation.RequireAuth;
import com.ticketbooking.common.constant.JwtConstants;
import com.ticketbooking.common.context.UserContext;
import com.ticketbooking.common.context.UserInfo;
import com.ticketbooking.common.enums.Role;
import com.ticketbooking.common.exception.ForbiddenException;
import com.ticketbooking.common.exception.UnauthorizedException;
import com.ticketbooking.common.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.lang.reflect.Method;

@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtils jwtUtils;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RequireAuth requireAuth = resolveRequireAuth(handlerMethod);
        if (requireAuth == null) {
            return true;
        }

        boolean requireAdmin = requiresAdmin(requireAuth);

        // 优先读取网关透传的 Header
        String userIdHeader = request.getHeader(JwtConstants.HEADER_USER_ID);
        String usernameHeader = request.getHeader(JwtConstants.HEADER_USERNAME);
        String isAdminHeader = request.getHeader(JwtConstants.HEADER_IS_ADMIN);

        if (userIdHeader != null && usernameHeader != null) {
            if (requireAdmin && !"true".equals(isAdminHeader)) {
                throw new ForbiddenException();
            }
            Long userId = Long.parseLong(userIdHeader);
            UserContext.setUserInfo(UserInfo.builder()
                    .userId(userId)
                    .username(usernameHeader)
                    .build());
            return true;
        }

        // 无透传 Header，解析 Token
        String authHeader = request.getHeader(JwtConstants.HEADER_AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith(JwtConstants.TOKEN_PREFIX)) {
            throw new UnauthorizedException();
        }

        String token = authHeader.substring(JwtConstants.TOKEN_PREFIX.length());
        Claims claims = jwtUtils.validateToken(token);
        if (claims == null) {
            throw new UnauthorizedException();
        }

        if (requireAdmin && !jwtUtils.isAdmin(claims)) {
            throw new ForbiddenException();
        }

        UserContext.setUserInfo(UserInfo.builder()
                .userId(jwtUtils.getUserId(claims))
                .username(jwtUtils.getUsername(claims))
                .token(token)
                .build());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }

    /**
     * 查找方法/类上的 @RequireAuth，findMergedAnnotation 会解析元注解，
     * 因此 @UserRateLimit（其上标注了 @RequireAuth）同样命中。
     */
    private RequireAuth resolveRequireAuth(HandlerMethod handlerMethod) {
        Method method = handlerMethod.getMethod();
        RequireAuth requireAuth = AnnotatedElementUtils.findMergedAnnotation(method, RequireAuth.class);
        if (requireAuth == null) {
            requireAuth = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getBeanType(), RequireAuth.class);
        }
        return requireAuth;
    }

    private boolean requiresAdmin(RequireAuth requireAuth) {
        for (Role role : requireAuth.value()) {
            if (role == Role.ADMIN) {
                return true;
            }
        }
        return false;
    }
}
