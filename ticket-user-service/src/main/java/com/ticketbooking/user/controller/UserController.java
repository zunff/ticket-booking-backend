package com.ticketbooking.user.controller;

import com.ticketbooking.common.result.Result;
import com.ticketbooking.user.converter.UserConverter;
import com.ticketbooking.user.entity.User;
import com.ticketbooking.user.service.UserService;
import com.ticketbooking.user.model.qo.LoginQO;
import com.ticketbooking.user.model.vo.LoginVO;
import com.ticketbooking.user.model.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserConverter userConverter;

    @PostMapping("/login")
    public Result<LoginVO> login(@RequestBody LoginQO qo) {
        try {
            LoginVO vo = userService.login(qo);
            return Result.success("登录成功", vo);
        } catch (RuntimeException e) {
            return Result.error(1001, e.getMessage());
        }
    }

    @PostMapping("/register")
    public Result<UserVO> register(@RequestBody User user) {
        try {
            User registeredUser = userService.register(user);
            return Result.success("注册成功", userConverter.toVO(registeredUser));
        } catch (RuntimeException e) {
            return Result.error(1002, e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public Result<UserVO> getUser(@PathVariable Long id) {
        User user = userService.getById(id);
        if (user == null) {
            return Result.error(1001, "用户不存在");
        }
        return Result.success(userConverter.toVO(user));
    }

    @PostMapping
    public Result<UserVO> createUser(@RequestBody User user) {
        if (userService.findByUsername(user.getUsername()) != null) {
            return Result.error(1002, "用户名已存在");
        }
        userService.save(user);
        return Result.success("用户创建成功", userConverter.toVO(user));
    }

    @GetMapping("/validate/{id}")
    public Result<Boolean> validateUser(@PathVariable Long id) {
        boolean exists = userService.validateUser(id);
        return Result.success(exists);
    }
}
