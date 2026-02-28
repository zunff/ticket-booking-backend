package com.ticketbooking.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ticketbooking.common.utils.JwtUtils;
import com.ticketbooking.user.dto.LoginRequest;
import com.ticketbooking.user.dto.LoginResponse;
import com.ticketbooking.user.entity.User;
import com.ticketbooking.user.mapper.UserMapper;
import com.ticketbooking.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {
    
    private final JwtUtils jwtUtils;
    
    @Override
    public User findByUsername(String username) {
        return getOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
    }
    
    @Override
    public boolean validateUser(Long userId) {
        return getById(userId) != null;
    }
    
    @Override
    public LoginResponse login(LoginRequest request) {
        User user = findByUsername(request.getUsername());
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        if (!user.getPassword().equals(request.getPassword())) {
            throw new RuntimeException("密码错误");
        }
        
        String token = jwtUtils.generateToken(user.getId(), user.getUsername());
        
        return LoginResponse.builder()
                .user(user)
                .token(token)
                .build();
    }
    
    @Override
    public User register(User user) {
        if (findByUsername(user.getUsername()) != null) {
            throw new RuntimeException("用户名已存在");
        }
        save(user);
        return user;
    }
}
