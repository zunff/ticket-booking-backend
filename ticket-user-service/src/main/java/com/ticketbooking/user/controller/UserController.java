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
        LoginVO vo = userService.login(qo);
        return Result.success("登录成功", vo);
    }

    @PostMapping("/register")
    public Result<UserVO> register(@RequestBody User user) {
        UserVO vo = userService.registerAndReturnVO(user);
        return Result.success("注册成功", vo);
    }

    @GetMapping("/{id}")
    public Result<UserVO> getUser(@PathVariable Long id) {
        UserVO vo = userService.getUserVOById(id);
        return Result.success(vo);
    }

    @PostMapping
    public Result<UserVO> createUser(@RequestBody User user) {
        UserVO vo = userService.registerAndReturnVO(user);
        return Result.success("用户创建成功", vo);
    }

    @GetMapping("/validate/{id}")
    public Result<Boolean> validateUser(@PathVariable Long id) {
        boolean exists = userService.validateUser(id);
        return Result.success(exists);
    }
}
