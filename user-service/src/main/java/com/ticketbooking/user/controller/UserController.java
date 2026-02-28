package com.ticketbooking.user.controller;

import com.ticketbooking.common.result.Result;
import com.ticketbooking.user.dto.LoginRequest;
import com.ticketbooking.user.dto.LoginResponse;
import com.ticketbooking.user.entity.User;
import com.ticketbooking.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {
    
    private final UserService userService;
    
    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody LoginRequest request) {
        try {
            LoginResponse response = userService.login(request);
            response.getUser().setPassword(null);
            return Result.success("登录成功", response);
        } catch (RuntimeException e) {
            return Result.error(1001, e.getMessage());
        }
    }
    
    @PostMapping("/register")
    public Result<User> register(@RequestBody User user) {
        try {
            User registeredUser = userService.register(user);
            registeredUser.setPassword(null);
            return Result.success("注册成功", registeredUser);
        } catch (RuntimeException e) {
            return Result.error(1002, e.getMessage());
        }
    }
    
    @GetMapping("/{id}")
    public Result<User> getUser(@PathVariable Long id) {
        User user = userService.getById(id);
        if (user == null) {
            return Result.error(1001, "用户不存在");
        }
        user.setPassword(null);
        return Result.success(user);
    }
    
    @PostMapping
    public Result<User> createUser(@RequestBody User user) {
        if (userService.findByUsername(user.getUsername()) != null) {
            return Result.error(1002, "用户名已存在");
        }
        userService.save(user);
        user.setPassword(null);
        return Result.success("用户创建成功", user);
    }
    
    @GetMapping("/validate/{id}")
    public Result<Boolean> validateUser(@PathVariable Long id) {
        boolean exists = userService.validateUser(id);
        return Result.success(exists);
    }
}
