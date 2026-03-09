package com.ticketbooking.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ticketbooking.user.entity.User;
import com.ticketbooking.user.model.qo.LoginQO;
import com.ticketbooking.user.model.vo.LoginVO;
import com.ticketbooking.user.model.vo.UserVO;

public interface UserService extends IService<User> {
    User findByUsername(String username);
    boolean validateUser(Long userId);
    LoginVO login(LoginQO qo);
    User register(User user);
    UserVO getUserVOById(Long id);
    UserVO registerAndReturnVO(User user);
}
