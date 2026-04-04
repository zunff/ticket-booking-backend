package com.ticketbooking.user.controller;

import com.ticketbooking.common.annotation.RequireAuth;
import com.ticketbooking.common.context.UserContext;
import com.ticketbooking.common.result.Result;
import com.ticketbooking.user.service.UserService;
import com.ticketbooking.user.model.qo.ChangePasswordQO;
import com.ticketbooking.user.model.qo.LoginQO;
import com.ticketbooking.user.model.qo.RegisterQO;
import com.ticketbooking.user.model.qo.UpdateProfileQO;
import com.ticketbooking.user.model.vo.LoginVO;
import com.ticketbooking.user.model.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "用户管理")
@RestController
@RequiredArgsConstructor
@RequestMapping
public class UserController {

    private final UserService userService;

    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public Result<LoginVO> login(@RequestBody LoginQO qo) {
        LoginVO vo = userService.login(qo);
        return Result.success("登录成功", vo);
    }

    @Operation(summary = "用户注册")
    @PostMapping("/register")
    public Result<LoginVO> register(@RequestBody RegisterQO qo) {
        LoginVO vo = userService.registerAndLogin(qo);
        return Result.success("注册成功", vo);
    }

    @Operation(summary = "获取当前用户信息")
    @PostMapping("/me")
    @RequireAuth
    public Result<UserVO> getCurrentUser() {
        Long userId = UserContext.getUserId();
        UserVO vo = userService.getUserVOById(userId);
        return Result.success(vo);
    }

    @Operation(summary = "更新个人信息")
    @PutMapping("/profile")
    @RequireAuth
    public Result<UserVO> updateProfile(@RequestBody UpdateProfileQO qo) {
        Long userId = UserContext.getUserId();
        UserVO vo = userService.updateProfile(userId, qo);
        return Result.success("个人信息更新成功", vo);
    }

    @Operation(summary = "修改密码")
    @PutMapping("/password")
    @RequireAuth
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordQO qo) {
        Long userId = UserContext.getUserId();
        userService.changePassword(userId, qo);
        return Result.success("密码修改成功", null);
    }

    @Operation(summary = "获取用户信息")
    @GetMapping("/{id}")
    public Result<UserVO> getUser(@PathVariable Long id) {
        UserVO vo = userService.getUserVOById(id);
        return Result.success(vo);
    }

    @Operation(summary = "验证用户是否存在")
    @GetMapping("/validate/{id}")
    public Result<Boolean> validateUser(@PathVariable Long id) {
        boolean exists = userService.validateUser(id);
        return Result.success(exists);
    }
}
