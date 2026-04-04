package com.ticketbooking.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ticketbooking.user.entity.User;
import com.ticketbooking.user.model.qo.ChangePasswordQO;
import com.ticketbooking.user.model.qo.LoginQO;
import com.ticketbooking.user.model.qo.RegisterQO;
import com.ticketbooking.user.model.qo.UpdateProfileQO;
import com.ticketbooking.user.model.vo.LoginVO;
import com.ticketbooking.user.model.vo.UserVO;

public interface UserService extends IService<User> {
    User findByUsername(String username);
    boolean validateUser(Long userId);
    LoginVO login(LoginQO qo);
    UserVO getUserVOById(Long id);
    UserVO updateProfile(Long userId, UpdateProfileQO qo);
    void changePassword(Long userId, ChangePasswordQO qo);
    LoginVO registerAndLogin(RegisterQO qo);
}
